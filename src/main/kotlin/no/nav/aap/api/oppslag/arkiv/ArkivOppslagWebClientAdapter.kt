package no.nav.aap.api.oppslag.arkiv

import graphql.kickstart.spring.webclient.boot.GraphQLWebClient
import no.nav.aap.api.felles.error.IntegrationException
import no.nav.aap.api.oppslag.OppslagController.Companion.DOKUMENT_PATH
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagConfig.Companion.DOKUMENTER_QUERY
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagConfig.Companion.SAF
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagJournalposter.ArkivOppslagJournalpost
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagJournalposter.ArkivOppslagJournalpost.ArkivOppslagDokumentInfo.ArkivOppslagDokumentVariant.ArkivOppslagDokumentFiltype.PDF
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagJournalposter.ArkivOppslagJournalpost.ArkivOppslagDokumentInfo.ArkivOppslagDokumentVariant.ArkivOppslagDokumentVariantFormat.*
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagJournalposter.ArkivOppslagJournalpost.ArkivOppslagJournalpostType
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagJournalposter.ArkivOppslagJournalpost.ArkivOppslagJournalpostType.I
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagJournalposter.ArkivOppslagJournalpost.ArkivOppslagJournalpostType.U
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagJournalposter.ArkivOppslagJournalpost.ArkivOppslagRelevantDato.ArkivOppslagDatoType.DATO_OPPRETTET
import no.nav.aap.api.oppslag.graphql.AbstractGraphQLAdapter
import no.nav.aap.util.AuthContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.net.URI
import java.time.LocalDateTime
import java.util.*

@Component
class ArkivOppslagWebClientAdapter(
        @Qualifier(SAF) client: WebClient,
        @Qualifier(SAF) private val graphQL: GraphQLWebClient,
        private val ctx: AuthContext,
        private val mapper: ArkivOppslagMapper,
        val cf: ArkivOppslagConfig) : AbstractGraphQLAdapter(client, cf) {

    fun dokument(journalpostId: String, dokumentInfoId: String) =
        webClient.get()
            .uri { b -> cf.dokUri(b, journalpostId, dokumentInfoId) }
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono<ByteArray>()
            .doOnSuccess { log.trace("Arkivoppslag returnerte  ${it.size} bytes") }
            .doOnError { t: Throwable -> log.warn("Arkivoppslag feilet", t) }
            .block() ?: throw IntegrationException("Null response fra arkiv")

    fun dokumenter() = query(graphQL,DOKUMENTER_QUERY, ctx.getFnr(),  ArkivOppslagJournalposter::class)
        ?.journalposter
        ?.filter { it.journalposttype in listOf(I, U) }
        ?.flatMap { mapper.tilDokumenter(it) }
        .orEmpty()

    fun søknadDokumentId(journalPostId: String) =query(graphQL,DOKUMENTER_QUERY, ctx.getFnr(),  ArkivOppslagJournalposter::class)
        ?.journalposter
        ?.firstOrNull { it.journalpostId == journalPostId }
        ?.dokumenter?.firstOrNull()?.dokumentInfoId
}
@Component
class ArkivOppslagMapper(@Value("\${ingress}") private val ingress: URI) {
    fun tilDokumenter(journalpost: ArkivOppslagJournalpost) =
        with(journalpost) {
            dokumenter.filter { v ->
                v.dokumentvarianter.any {
                    it.filtype == PDF && it.brukerHarTilgang && ARKIV == it.variantformat
                }
            }.map { dok ->
                DokumentOversiktInnslag(
                        journalpostId, dok.dokumentInfoId,
                        dok.tittel,
                        journalposttype,
                        eksternReferanseId,
                        relevanteDatoer.first {
                            it.datotype == DATO_OPPRETTET
                        }.dato)
            }.sortedByDescending { it.dato }
        }

    data class DokumentOversiktInnslag(val journalpostId: String,
                                       val dokumentId: String,
                                       val tittel: String?,
                                       val type: ArkivOppslagJournalpostType,
                                       val innsendingId: UUID?,
                                       val dato: LocalDateTime)

}