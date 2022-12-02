package cats_sangria.fs2sangria

import cats.syntax.all.*
import cats.effect.Async
import cats.effect.std.Dispatcher
import fs2.Stream
import sangria.streaming.SubscriptionStream

import scala.concurrent.Future

class Fs2StreamSubscriptionStream[F[_]: Async](dispatcher: Dispatcher[F]) extends SubscriptionStream[Stream[F, *]]:
  override def supported[T[_]](other: SubscriptionStream[T]): Boolean =
    other.isInstanceOf[Fs2StreamSubscriptionStream[?]]

  override def singleFuture[T](value: Future[T]): Stream[F, T] =
    Stream.eval(Async[F].fromFuture(value.pure))

  override def single[T](value: T): Stream[F, T] =
    Stream(value)

  override def recover[T](stream: Stream[F, T])(fn: Throwable => T): Stream[F, T] =
    stream.handleError(fn)

  override def onComplete[Ctx, Res](result: Stream[F, Res])(op: => Unit): Stream[F, Res] =
    result.onFinalize(Async[F].delay(op))

  override def merge[T](streams: Vector[Stream[F, T]]): Stream[F, T] =
    streams.foldLeft(Stream[F, T]())(_.merge(_))

  override def mapFuture[A, B](source: Stream[F, A])(fn: A => Future[B]): Stream[F, B] =
    source.evalMap(a => Async[F].fromFuture(fn(a).pure))

  override def map[A, B](source: Stream[F, A])(fn: A => B): Stream[F, B] =
    source.map(fn)

  override def flatMapFuture[Ctx, Res, T](future: Future[T])(resultFn: T => Stream[F, Res]): Stream[F, Res] =
    Stream.eval(Async[F].fromFuture(future.pure)).flatMap(resultFn)

  override def first[T](s: Stream[F, T]): Future[T] =
    dispatcher.unsafeToFuture(s.take(1).compile.lastOrError)

  override def failed[T](e: Throwable): Stream[F, T] = Stream.raiseError(e)

given fs2StreamSubscriptionStream[F[_]: Async: Dispatcher]: SubscriptionStream[Stream[F, *]] =
  new Fs2StreamSubscriptionStream(summon[Dispatcher[F]])