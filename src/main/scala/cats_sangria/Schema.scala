package cats_sangria

import cats.effect.{Async, Sync}
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import sangria.schema.*
import sangria.streaming.SubscriptionStream

import java.time.{OffsetDateTime, ZoneId, ZonedDateTime}
import scala.concurrent.Future
import scala.concurrent.duration.*

import fs2.*
import fs2.concurrent.Topic

class Schema[F[_]: Async](topic: Topic[F, String])(using SubscriptionStream[fs2.Stream[F, *]]):
  extension [A](self: F[A])
    def respond[C]: DeferredValue[C, A] =
      DeferredValue(AsyncDeferred(self))

  private val Name = Argument("name", OptionInputType(StringType))
  private val Message = Argument("message", StringType)

  val QueryType: ObjectType[Unit, Unit] = ObjectType("Query", fields[Unit, Unit](
    Field(
      name = "hello",
      fieldType = StringType,
      description = Some("A field that says hello to you!"),
      arguments = List(Name),
      resolve = { c =>
        val name = c.arg(Name).getOrElse("Stranger")
        Sync[F].delay(show"Hello, $name").respond
      }
    )
  ))

  val MutationType = ObjectType("Mutation", fields[Unit, Unit](
    Field(
      name = "sendMessage",
      fieldType = StringType,
      description = Some("Send a message to all subscribers"),
      arguments = List(Message),
      resolve = { c =>
        val message = c.arg(Message)
        topic.publish1(message).as(message).respond
      }
    )
  ))

  def action[Ctx, Val](action: Action[Ctx, Val]): Action[Ctx, Val] = action

  val SubscriptionType = ObjectType("Subscription", fields[Unit, Unit](
    Field.subs("messages", StringType,
      resolve = _ =>
        topic.subscribe(10).scan(0 -> Option.empty[String]) { (state, message) =>
          val n = state._1 + 1
          (n, Some(s"Message ${n}: $message"))
        }.collect { case (_, Some(str)) => str }.map(action(_))
    )
  ))

  val schema: sangria.schema.Schema[Unit, Unit] = sangria.schema.Schema(
    query = QueryType,
    mutation = Some(MutationType),
    subscription = Some(SubscriptionType)
  )

object Schema:
  def apply[F[_]: Async](topic: Topic[F, String], subscriptionStream: SubscriptionStream[fs2.Stream[F, *]]): Schema[F] =
    new Schema(topic)(using Async[F], subscriptionStream)
