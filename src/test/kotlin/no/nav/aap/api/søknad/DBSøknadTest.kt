package no.nav.aap.api.søknad

import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import java.net.URI
import java.time.Duration.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.fail
import no.nav.aap.api.config.Metrikker
import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.felles.SkjemaType
import no.nav.aap.api.oppslag.arkiv.ArkivOppslagClient
import no.nav.aap.api.oppslag.søknad.SøknadClient
import no.nav.aap.api.saksbehandling.SaksbehandlingController.VedleggEtterspørsel
import no.nav.aap.api.søknad.SøknadTest.Companion.standardSøknad
import no.nav.aap.api.søknad.arkiv.ArkivClient.ArkivResultat
import no.nav.aap.api.søknad.fordeling.SøknadFullfører
import no.nav.aap.api.søknad.fordeling.SøknadRepository
import no.nav.aap.api.søknad.fordeling.SøknadRepository.Companion.SISTE_SØKNAD
import no.nav.aap.api.søknad.mellomlagring.BucketConfig.MellomlagringBucketConfig
import no.nav.aap.api.søknad.mellomlagring.Mellomlager
import no.nav.aap.api.søknad.mellomlagring.dokument.DokumentInfo
import no.nav.aap.api.søknad.mellomlagring.dokument.Dokumentlager
import no.nav.aap.api.søknad.minside.MinSideBeskjedRepository
import no.nav.aap.api.søknad.minside.MinSideClient
import no.nav.aap.api.søknad.minside.MinSideConfig
import no.nav.aap.api.søknad.minside.MinSideConfig.BacklinksConfig
import no.nav.aap.api.søknad.minside.MinSideConfig.NAISConfig
import no.nav.aap.api.søknad.minside.MinSideConfig.TopicConfig
import no.nav.aap.api.søknad.minside.MinSideConfig.UtkastConfig
import no.nav.aap.api.søknad.minside.MinSideOppgaveRepository
import no.nav.aap.api.søknad.minside.MinSideProdusenter
import no.nav.aap.api.søknad.minside.MinSideRepositories
import no.nav.aap.api.søknad.minside.MinSideUtkastRepository
import no.nav.aap.api.søknad.model.StandardEttersending
import no.nav.aap.api.søknad.model.StandardEttersending.EttersendtVedlegg
import no.nav.aap.api.søknad.model.StandardSøknad
import no.nav.aap.api.søknad.model.Vedlegg
import no.nav.aap.api.søknad.model.VedleggType
import no.nav.aap.api.søknad.model.VedleggType.*
import no.nav.aap.util.AuthContext
import no.nav.aap.util.LoggerUtil
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.*
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.concurrent.ListenableFuture
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = NONE)
@DataJpaTest
class DBSøknadTest {

    val log = LoggerUtil.getLogger(javaClass)


    @Autowired
    lateinit var søknadRepo: SøknadRepository

    @Autowired
    lateinit var beskjedRepo: MinSideBeskjedRepository

    @Autowired
    lateinit var oppgaveRepo: MinSideOppgaveRepository

    @Autowired
    lateinit var utkastRepo: MinSideUtkastRepository

    @Mock
    lateinit var  arkivClient: ArkivOppslagClient
    @Mock
    lateinit var ctx: AuthContext
    @Mock
    lateinit var avro: KafkaOperations<NokkelInput, Any>

    @Mock
    lateinit var utkast: KafkaOperations<String, String>

    @Mock
    lateinit var result: ListenableFuture<SendResult<NokkelInput, Any>>


    @BeforeEach
    fun init() {
        `when`(ctx.getFnr()).thenReturn(FNR)
        `when`(avro.send(any<ProducerRecord<NokkelInput,Any>>())).thenReturn(result)
        `when`(result.get()).thenReturn(RESULT)
    }

    @Test
    @DisplayName("Etterspørr vedlegg, sjekk at oppgave opprettes og mangelen lagres i DB, ettersend vedlegg og sjekk at oppgaven avsluttes og mangelen fjernes fra DB")
    fun testEtterspørrManglende() {
        val minSide = MinSideClient(MinSideProdusenter(avro,utkast),CFG, MinSideRepositories(beskjedRepo,oppgaveRepo,utkastRepo,søknadRepo))
        val fullfører = SøknadFullfører(InMemoryDokumentLager(), minSide, søknadRepo, InMemoryMellomLager(FNR), Metrikker(LoggingMeterRegistry()))
        val søknadClient = SøknadClient(søknadRepo,arkivClient,minSide,ctx)
        val søknadId = fullfører.fullfør(FNR, SØKNAD, ARKIVRESULTAT).uuid ?: fail("Søknad ikke registrert")
        val søknad = søknadRepo.getSøknadByFnr(FNR.fnr,SISTE_SØKNAD).first()
        assertEquals( 1,søknad.manglendevedlegg.size)
        val oppgaveId = søknadClient.etterspørrVedlegg(VedleggEtterspørsel(FNR,LÅNEKASSEN_LÅN)) ?: fail("Etterspørsel ikke registrert")
        val dto = søknadClient.søknad(søknadId) ?:  fail("Søknad ikke registrert")
        assertEquals(2,dto.manglendeVedlegg.size)
        assertEquals(2,søknad.innsendtevedlegg.size)
        assertEquals(2,søknad.oppgaver.size)
        assertEquals(søknadId,søknad.oppgaver.first().eventid)
        assertEquals(oppgaveId,søknad.oppgaver.last().eventid)
        log.trace("Oppgaver for søknad er ${søknad.oppgaver}")
        fullfører.fullfør(FNR, ettesending(søknad.eventid,LÅNEKASSEN_LÅN), ARKIVRESULTAT)
        log.trace("Oppgaver etter fullføring for søknad er ${søknad.oppgaver}")
        assertNull(søknad.oppgaver.find { it.eventid == oppgaveId })
        assertNotNull(søknad.oppgaver.find { it.eventid == søknad.eventid })
        assertEquals(1,søknad.oppgaver.size)
        fullfører.fullfør(FNR, ettesending(søknad.eventid,ANDREBARN), ARKIVRESULTAT)
        assertEquals(0,søknad.oppgaver.size)
    }

    companion object  {
        private val SØKNAD = standardSøknad()
        private val NAV = URI.create("http://www.nav.no")
        private val ARKIVRESULTAT = ArkivResultat("42", listOf("666"))
        private val RESULT = SendResult<NokkelInput, Any>(null, RecordMetadata(TopicPartition("p",1),0,0,0,0,0))
        private val CFG = MinSideConfig(NAISConfig("aap","soknad-api"),
                TopicConfig("beskjed", ofDays(1), true, emptyList(),4),
                TopicConfig("oppgave", ofDays(1), true, emptyList(),4),
                UtkastConfig("utkast", true),
                true,
                BacklinksConfig(NAV, NAV, NAV),"done")
        private val  FNR = Fødselsnummer("08089403198")
        @BeforeAll
        internal fun startDB() {
            PostgreSQLContainer<Nothing>("postgres:14:5").apply { start()
            }
        }
        internal fun ettesending(id: UUID,  type: VedleggType) = StandardEttersending(id, listOf(EttersendtVedlegg(Vedlegg(),type)))
    }
}
internal class InMemoryMellomLager(private val fnr: Fødselsnummer): Mellomlager {

    private val lager = mutableMapOf<Fødselsnummer,String>()

    override fun lagre(value: String, type: SkjemaType) =
        with(fnr) {
            lager[this] = value
            this.fnr
        }

    override fun les(type: SkjemaType) = lager[fnr]

    override fun slett(type: SkjemaType) = lager.remove(fnr) != null

    override fun config(): MellomlagringBucketConfig {
        TODO("Not yet implemented")
    }

}
internal class InMemoryDokumentLager: Dokumentlager {
    private val lager = mutableMapOf<UUID,DokumentInfo>()
    override fun lesDokument(uuid: UUID): DokumentInfo? = lager[uuid]
    override fun slettDokumenter(uuids: List<UUID>) = uuids.forEach { lager.remove(it)}
    override fun slettDokumenter(søknad: StandardSøknad) = lager.clear()

    override fun lagreDokument(dokument: DokumentInfo) =
        with(UUID.randomUUID()) {
            lager[this] = dokument
            this
        }

    override fun slettAlleDokumenter() = lager.clear()
    override fun slettAlleDokumenter(fnr: Fødselsnummer) = slettAlleDokumenter()
}