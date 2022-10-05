package no.nav.aap.api.error

import com.fasterxml.jackson.databind.DatabindException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.google.cloud.storage.StorageException
import no.nav.aap.api.felles.error.IntegrationException
import no.nav.aap.api.søknad.mellomlagring.DokumentException
import no.nav.aap.api.søknad.mellomlagring.DokumentException.Substatus
import no.nav.aap.api.søknad.mellomlagring.dokument.GCPKryptertDokumentlager.ContentTypeDokumentSjekker.ContentTypeException
import no.nav.aap.util.LoggerUtil.getLogger
import no.nav.aap.util.MDCUtil.NAV_CALL_ID
import no.nav.aap.util.MDCUtil.callId
import no.nav.security.token.support.core.exceptions.JwtTokenMissingException
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.client.HttpClientErrorException.NotFound
import org.springframework.web.context.request.NativeWebRequest
import org.zalando.problem.Problem
import org.zalando.problem.Problem.builder
import org.zalando.problem.Status
import org.zalando.problem.Status.BAD_REQUEST
import org.zalando.problem.Status.INTERNAL_SERVER_ERROR
import org.zalando.problem.Status.NOT_FOUND
import org.zalando.problem.Status.SERVICE_UNAVAILABLE
import org.zalando.problem.Status.UNAUTHORIZED
import org.zalando.problem.Status.UNPROCESSABLE_ENTITY
import org.zalando.problem.Status.UNSUPPORTED_MEDIA_TYPE
import org.zalando.problem.spring.web.advice.ProblemHandling

@ControllerAdvice
class AAPApiExceptionHandling : ProblemHandling {
    private val log = getLogger(javaClass)

    @ExceptionHandler(JwtTokenMissingException::class, JwtTokenUnauthorizedException::class)
    fun auth(e: RuntimeException, req: NativeWebRequest) = problem(e, UNAUTHORIZED, req)

    @ExceptionHandler(IntegrationException::class, StorageException::class)
    fun inegration(e: RuntimeException, req: NativeWebRequest) = problem(e, SERVICE_UNAVAILABLE, req)

    @ExceptionHandler(ContentTypeException::class)
    fun ukjent(e: ContentTypeException, req: NativeWebRequest) = problem(e, UNSUPPORTED_MEDIA_TYPE, req)

    @ExceptionHandler(IllegalArgumentException::class, DatabindException::class)
    fun illegal(e: Exception, req: NativeWebRequest) = problem(e, BAD_REQUEST, req)

    @ExceptionHandler(NotFound::class)
    fun ikkeFunnet(e: NotFound, req: NativeWebRequest) = problem(e, NOT_FOUND, req)

    @ExceptionHandler(DokumentException::class)
    fun dokument(e: DokumentException, req: NativeWebRequest) =
        create(e, problem(e, UNPROCESSABLE_ENTITY, e.substatus), req)

    @ExceptionHandler
    fun handle(e: MethodArgumentNotValidException, request: NativeWebRequest): ResponseEntity<Problem> =
        create(e,problem(e,BAD_REQUEST), request)
    @ExceptionHandler
    fun handle(e: InvalidFormatException, request: NativeWebRequest): ResponseEntity<Problem> =
        create(e,problem(e,BAD_REQUEST), request)

    @ExceptionHandler(Exception::class)
    fun catchAll(e: Exception, req: NativeWebRequest) = create(e, problem(e, INTERNAL_SERVER_ERROR), req)

    fun problem(t: Throwable, status: Status, req: NativeWebRequest) = create(t, problem(t, status), req)

    private fun problem(t: Throwable, status: Status, substatus: Substatus? = null) =
        with(builder().withStatus(status).withDetail(t.message).with(NAV_CALL_ID, callId())) {
            substatus?.let {
                with("substatus", it).build()
            } ?: build()
        }.also {
            log.trace("Returnerer $status", it)
        }
}