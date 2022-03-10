package no.nav.aap.api.oppslag.arbeidsforhold

import no.nav.aap.rest.AbstractRestConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue
import java.net.URI


@ConfigurationProperties(prefix = "arbeidsforhold")
@ConstructorBinding
class ArbeidsforholdConfig(@DefaultValue(DEFAULT_URI) baseUri: URI,
                          @DefaultValue("true") enabled: Boolean): AbstractRestConfig(baseUri, "ping", enabled) {

    companion object {
        const val DEFAULT_URI  ="https://aareg-services-q1.dev.intern.nav.no/aareg-services"
        const val ARBEIDSFORHOLD = "arbeidsforhold"
    }
}