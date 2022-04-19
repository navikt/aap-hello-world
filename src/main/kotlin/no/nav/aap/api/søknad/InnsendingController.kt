package no.nav.aap.api.søknad

import no.nav.aap.api.søknad.formidling.LegacyStandardSøknadKafkaFormidler
import no.nav.aap.api.søknad.formidling.StandardSøknadFormidler
import no.nav.aap.api.søknad.formidling.UtlandSøknadFormidler
import no.nav.aap.api.søknad.model.StandardSøknad
import no.nav.aap.api.søknad.model.Kvittering
import no.nav.aap.api.søknad.model.UtenlandsSøknad
import no.nav.aap.util.Constants.IDPORTEN
import no.nav.security.token.support.spring.ProtectedRestController
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import javax.validation.Valid

@ProtectedRestController(value = ["/innsending"], issuer = IDPORTEN)
class InnsendingController(
        private val legacyFormidler: LegacyStandardSøknadKafkaFormidler,
        private val utenlandsFormidler: UtlandSøknadFormidler,
        private val standardFormidler: StandardSøknadFormidler) {

    @PostMapping("/utland")
    fun utland(@RequestBody søknad: @Valid UtenlandsSøknad): Kvittering {
        utenlandsFormidler.formidle(søknad)
        return Kvittering("OK")
    }

    @PostMapping("/soknad")
    fun standard(): Kvittering {
        legacyFormidler.formidle()
        return Kvittering("OK")
    }
    @PostMapping("/soknadny")
    fun standardNy(@RequestBody søknad: @Valid StandardSøknad): Kvittering {
        standardFormidler.formidle(søknad)
        return Kvittering("OK")
    }

    override fun toString() = "$javaClass.simpleName [standardFormidler=$standardFormidler,utenlandsFormidler=$utenlandsFormidler]"
}