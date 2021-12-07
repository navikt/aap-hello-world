package no.nav.aap.api.mellomlagring

import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.søknad.SkjemaType
import no.nav.aap.rest.UnprotectedRestController
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody


@UnprotectedRestController(value = ["dev/buckets"])
class MellomlagringDevController(private val gcp: Mellomlagring) {

    @PostMapping("/lagre/{fnr}/{type}")
    fun lagre(@PathVariable fnr: Fødselsnummer,
              @PathVariable type: SkjemaType,
              @RequestBody data: String): ResponseEntity<String> {
        gcp.lagre(fnr, type, data)
        return ResponseEntity<String>(data, CREATED)
    }

    @GetMapping("/les/{fnr}/{type}")
    fun les(@PathVariable fnr: Fødselsnummer, @PathVariable type: SkjemaType) = gcp.les(fnr, type)


    @DeleteMapping("/slett/{fnr}/{type}")
    fun slett(@PathVariable fnr: Fødselsnummer, @PathVariable type: SkjemaType): ResponseEntity<Void> {
        gcp.slett(fnr, type)
        return ResponseEntity<Void>(NO_CONTENT)
    }
}