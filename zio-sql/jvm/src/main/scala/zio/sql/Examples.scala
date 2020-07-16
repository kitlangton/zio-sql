package zio.sql

import zio.sql.ShopSchema.Orders.orders
import scala.language.implicitConversions
import zio.sql.ShopSchema.Users.users
import zio.sql._

object Examples {

  import ShopSchema._
  import ShopSchema.AggregationDef._
  import ShopSchema.Users._
  import ShopSchema.Orders._
  import ShopSchema.OrderDetails._

  //select first_name, last_name from users
  //val basicSelect = select { fName ++ lName } from userTable //todo fix issue #28

  //select first_name as first, last_name as last from users
  val basicSelectWithAliases = (select {
    (fName as "first") ++ (lName as "last")
  } from users)

  //select top 2 first_name, last_name from users order by last_name, first_name desc
  val selectWithRefinements =
    (select {
      (fName as "first_name") ++ (lName as "last_name") //todo fix #28 get rid of aliases
    }
      from users)
      .limit(2)
      .orderBy(
        lName //defaults to ascending order, do not need to specify .asc
        ,
        fName.desc //desc must be specified if you want descending order
      )

  //delete from users where first_name = 'Terrence'
  val basicDelete = deleteFrom(users).where(fName === "Terrence")

  //NOT VALID todo fix issue #37
  //Incorrect syntax near the keyword 'inner'.
  //delete from orders inner join users on orders.usr_id = users.usr_id where first_name = 'Terrence'
  val invalidJoinDelete = deleteFrom((orders join users).on(fkUserId === userId)).where(fName === "Terrence")

  private val uuids: Read[Int] = Read.lit(1, 2, 3)
  val deleteFromWithLiteral = deleteFrom(orders).where(fkUserId in uuids)

  implicit def select2SingleColumn[F,A,B](select: Read.Select[F, A, SelectionSet.Cons[A, B, SelectionSet.Empty]]): Read[B] =
    Read.SingleColumnSelect(select)

  val singleColumnSelect : Read[Int] = select(userId as "id") from users where (fName === "Fred") //todo fix issue #36
//  val singleColumnSelect  = select(userId as "id") from users where (fName === "Fred") //todo fix issue #36

  val deleteFromWithSubquery: ShopSchema.Delete[ShopSchema.Features.Source, orders.TableType] = deleteFrom(orders).where(fkUserId.in(
    singleColumnSelect
//    select(userId as "id") from users where (fName === "Fred") //todo fix issue #36
  ))

  //select first_name, last_name, order_date from users left join orders on users.usr_id = orders.usr_id
  val basicJoin = select {
    (fName as "first_name") ++ (lName as "last_name") ++ (orderDate as "order_date")
  } from (users leftOuter orders).on(fkUserId === userId)

  /*
    select users.usr_id, first_name, last_name, sum(quantity * unit_price) as "total_spend"
    from users
        left join orders on users.usr_id = orders.usr_id
        left join order_details on orders.order_id = order_details.order_id
    group by users.usr_id, first_name, last_name */

  val orderValues = (select {
    (Arbitrary(userId) as "usr_id") ++
      (Arbitrary(fName) as "first_name") ++
      (Arbitrary(lName) as "last_name") ++
      (Sum(quantity * unitPrice) as "total_spend")
  }
    from {
    users
      .join(orders)
      .on(userId === fkUserId)
      .leftOuter(orderDetails)
      .on(orderId == fkOrderId)
  })
    .groupBy(userId, fName /*, lName */) //shouldn't compile without lName todo fix #38
}

object ShopSchema extends Sql {
  self =>

  import self.ColumnSet._

  object Users {
    val users = (int("usr_id") ++ localDate("dob") ++ string("first_name") ++ string("last_name")).table("users")

    val userId :*: dob :*: fName :*: lName :*: _ = users.columns
  }

  object Orders {
    val orders = (int("order_id") ++ int("usr_id") ++ localDate("order_date")).table("orders")

    val orderId :*: fkUserId :*: orderDate :*: _ = orders.columns
  }

  object Products {
    val products =
      (int("product_id") ++ string("name") ++ string("description") ++ string("image_url")).table("products")

    val productId :*: description :*: imageURL :*: _ = products.columns
  }

  object ProductPrices {
    val productPrices =
      (int("product_id") ++ offsetDateTime("effective") ++ bigDecimal("price")).table("product_prices")

    val fkProductId :*: effective :*: price :*: _ = productPrices.columns
  }

  object OrderDetails {
    val orderDetails =
      (int("order_id") ++ int("product_id") ++ double("quantity") ++ double("unit_price"))
        .table("order_details") //todo fix #3 quantity should be int, unit price should be bigDecimal, numeric operators only support double ATM.

    val fkOrderId :*: fkProductId :*: quantity :*: unitPrice :*: _ = orderDetails.columns
  }

}
