package no.nav.aap.api.søknad.minside

import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.util.*
import no.nav.aap.api.søknad.minside.MinSideBeskjedRepository.Beskjed
import no.nav.aap.api.søknad.minside.MinSideRepository.EksternNotifikasjonBaseEntity
import no.nav.aap.api.søknad.minside.MinSideRepository.MinSideBaseEntity
import no.nav.aap.util.StringExtensions.partialMask

interface MinSideBeskjedRepository : MinSideRepository<Beskjed> {

    fun findByFnrAndDoneIsFalseAndMellomlagringIsTrueAndEventidNot(fnr: String, eventid: UUID): List<Beskjed>


    @Entity(name = "beskjed")
    @Table(name = "minsidebeskjeder")
    class Beskjed(fnr: String,
                  eventid: UUID,
                  val mellomlagring: Boolean = false,
                  done: Boolean = false,
                  ekstern: Boolean = false,
                  @OneToMany(mappedBy = "beskjed", cascade = [ALL], orphanRemoval = true)
                  var notifikasjoner: MutableSet<EksternBeskjedNotifikasjon> = mutableSetOf()) :
        MinSideBaseEntity(fnr, eventid, done,ekstern)  {
        override fun toString() = "${javaClass.simpleName} [fnr=${fnr.partialMask()}, created=$created, eventid=$eventid, updated=$updated,mellomlagring=$mellomlagring,ekstern=$ekstern, done=$done,id=$id]"
    }



    @Entity(name = "eksternbeskjednotifikasjon")
    @Table(name = "eksternebeskjednotifikasjoner")
    class EksternBeskjedNotifikasjon(@ManyToOne(optional = false) var beskjed: Beskjed? = null,
                                     eventid: UUID,
                                     distribusjonid: Long,
                                     distribusjonkanal: String) :
        EksternNotifikasjonBaseEntity(eventid, distribusjonid, distribusjonkanal)
}