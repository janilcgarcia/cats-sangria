package cats_sangria

import cats.syntax.all.*
import cats.effect.{Async, Sync}
import cats.effect.std.Dispatcher
import sangria.execution.deferred.{Deferred, DeferredResolver}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag

class AsyncDeferred[F[_]: Async, A](action: F[A]) extends Deferred[A]:
  def asyncInstance: Async[F] = Async[F]

  def fulfill(p: Promise[A]): F[Unit] =
    for {
      result <- action
      _ <- Sync[F].delay(p.success(result))
    } yield ()

class Resolver[F[_]: Async](dispatcher: Dispatcher[F]) extends DeferredResolver[Any] :
  override def resolve(deferred: Vector[Deferred[Any]],
                       ctx: Any, queryState: Any
                      )(implicit ec: ExecutionContext): Vector[Future[Any]] =
    val properDeferreds = deferred.collect {
      case d: AsyncDeferred[_, _] if d.asyncInstance == Async[F] =>
        d.asInstanceOf[AsyncDeferred[F, Any]]
    }

    val promises = Vector.fill(properDeferreds.size)(Promise[Any]())

    val actions = properDeferreds.zip(promises).traverse { (deferred, p) =>
      deferred.fulfill(p)
    }

    dispatcher.unsafeRunAndForget(actions)

    promises.map(_.future)
