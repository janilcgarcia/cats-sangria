package cats_sangria

import cats.effect.kernel.Resource.ExitCase

import scala.util.{Failure, Success, Try}
import cats.syntax.all.*
import cats.effect.std.Dispatcher
import cats.effect.{IO, IOApp, Resource}
import cats_sangria.fs2sangria.Fs2StreamSubscriptionStream
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.dsl.io.*
import org.http4s.circe.*

import scala.concurrent.duration.*
import com.comcast.ip4s.port
import io.circe.*
import io.circe.syntax.*
import sangria.execution.{ExecutionScheme, Executor}
import sangria.marshalling.circe.*
import sangria.parser.QueryParser
import sangria.streaming.SubscriptionStream

import scala.concurrent.{ExecutionContext, Future}
import fs2.Stream
import fs2.concurrent.Topic
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import sangria.ast.OperationType
import org.http4s.circe.CirceEntityCodec.given


object Server extends IOApp.Simple:
  private def service(schema: Schema[IO], sub: SubscriptionStream[Stream[IO, *]], dispatcher: Dispatcher[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "seconds" / IntVar(seconds) =>
      Ok(Stream.awakeEvery[IO](seconds.seconds).map(_.toString + "\n").through(fs2.text.utf8.encode))

    case GET -> Root / "graphql" =>
      StaticFile.fromResource[IO]("index.html").getOrElseF(NotFound())

    case req@POST -> Root / "graphql" =>
      for {
        json <- req.as[Json]
        given ExecutionContext <- IO.executionContext

        response <- {
          val ast = json.hcursor.get[String]("query").toTry.flatMap(QueryParser.parse(_))
          val variables = json.hcursor.get[JsonObject]("variables").getOrElse(JsonObject())
          val operationName = json.hcursor.get[String]("operationName").toOption

          ast match {
            case Success(ast) =>
              val isSubscription = ast.operations(operationName).operationType == OperationType.Subscription

              def execute()(using ExecutionScheme) =
                Executor.execute(
                  schema.schema,
                  queryAst = ast,
                  userContext = (),
                  variables = Json.fromJsonObject(variables),
                  operationName = operationName,
                  deferredResolver = new Resolver[IO](dispatcher)
                )

              if isSubscription then
                import ExecutionScheme.Stream
                given SubscriptionStream[Stream[IO, *]] = sub

                val stream: Stream[IO, Json] = execute()

                Ok(stream.handleErrorWith { error =>
                  Stream.emit(Map(
                    "errors" -> List(error.getMessage)
                  ).asJson)
                }.map(_.noSpaces + "\n").through(fs2.text.utf8.encodeC))
              else
                IO.fromFuture(IO(execute())).flatMap(Ok(_))

            case Failure(ex) =>
              InternalServerError(ex.getMessage)
          }
        }
      } yield response
  }

  private def buildServer(schema: Schema[IO], sub: SubscriptionStream[Stream[IO, *]], dispatcher: Dispatcher[IO]) =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(service(schema, sub, dispatcher).orNotFound)
      .withPort(port"5000")
      .withShutdownTimeout(100.millis)
      .withHttp2
      .build

  override val run: IO[Unit] =
    (for {
      dispatcher <- Dispatcher.parallel[IO]
      messageTopic <- Resource.eval(Topic[IO, String])
      sub = new Fs2StreamSubscriptionStream[IO](dispatcher)
      schema = Schema(messageTopic, sub)
      _ <- buildServer(schema, sub, dispatcher)
    } yield ()).useForever
