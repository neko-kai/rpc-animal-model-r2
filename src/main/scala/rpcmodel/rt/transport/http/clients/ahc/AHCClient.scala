package rpcmodel.rt.transport.http.clients.ahc

import java.net.URL
import java.util.function.BiFunction

import io.circe.{Json, Printer}
import izumi.functional.bio.BIO._
import izumi.functional.bio.BIOAsync
import org.asynchttpclient.{AsyncHttpClient, BoundRequestBuilder, Response}
import rpcmodel.rt.transport.codecs.IRTCodec
import rpcmodel.rt.transport.dispatch.GeneratedServerBase.ClientResponse
import rpcmodel.rt.transport.dispatch.{ClientTransport, CtxDec, GeneratedServerBase}
import rpcmodel.rt.transport.errors.ClientDispatcherError


class AHCClient[F[+_, +_]: BIOAsync, C](c: AsyncHttpClient, target: URL, printer: Printer, ctx: CtxDec[F, ClientDispatcherError, Response, C]) extends ClientTransport[F, C, Json] {
  override def dispatch(methodId: GeneratedServerBase.MethodId, body: Json): F[ClientDispatcherError, ClientResponse[C, Json]] = {
    import io.circe.parser._

    for {
      resp <- F.async[ClientDispatcherError, Response] {
        f =>

          val handler = new BiFunction[Response, Throwable, Unit] {
            override def apply(t: Response, u: Throwable): Unit = {
              if (t != null) {
                f(Right(t))
              } else {
                f(Left(ClientDispatcherError.UnknownException(u)))
              }
            }
          }

          prepare(methodId, body).execute().toCompletableFuture.handle[Unit](handler)

      }
      c <- ctx.decode(resp)
      body = resp.getResponseBody
      parsed <- F.fromEither(parse(body))
        .leftMap(e => ClientDispatcherError.ClientCodecFailure(List(IRTCodec.IRTCodecFailure.IRTParserException(e))))
    } yield {
      ClientResponse(c, parsed)
    }
  }

  private def prepare(methodId: GeneratedServerBase.MethodId, body: Json): BoundRequestBuilder = {
    val url = new URL(target.getProtocol, target.getHost, target.getPort, s"${target.getFile}/${methodId.service.name}/${methodId.method.name}")
    c
      .preparePost(url.toString)
      .setBody(body.printWith(printer))
  }
}
