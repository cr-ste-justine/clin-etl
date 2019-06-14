package org.crsj.clin.etl

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.expr
import org.crsj.clin.etl.DataFrameUtils.{joinAggregateFirst, joinAggregateList}
import org.elasticsearch.spark.sql._


object ETL {

  def run(base: String)(implicit spark: SparkSession): Unit = {
    import spark.implicits._
    val patients = Patient.load(base)
    val observations = Observation.load(base)
    val specimens = Specimen.load(base)
    val clinicalImpressions = ClinicalImpression.load(base)
    val practitionerWithRoles = Practitioners.load(base)
    val organizations = Organization.load(base)
    val serviceRequest = ServiceRequest.load(base)
    val studyWithPatients = Study.load(base)
    val familyMemberHistory = FamilyMemberHistory.load(base)

    val withObservations = joinAggregateList(patients, observations, patients("id") === $"subject.id", "observations")
    val withPractitioners = joinAggregateList(withObservations, practitionerWithRoles, expr("array_contains(generalPractitioner.id, role_id)"), "practitioners")
    val withSpecimens = joinAggregateList(withPractitioners, specimens, withPractitioners("id") === $"subject.id", "specimens")
    val withClinicalImpressions = joinAggregateList(withSpecimens, clinicalImpressions, withSpecimens("id") === $"subject.id", "clinicalImpressions")
    val withOrganizations = joinAggregateFirst(withClinicalImpressions, organizations, withClinicalImpressions("managingOrganization.id") === organizations("id"), "organization")
    val withServiceRequest = joinAggregateList(withOrganizations, serviceRequest, withOrganizations("id") === $"subject.id", "serviceRequests")
    val withStudy = joinAggregateList(withServiceRequest, studyWithPatients, withServiceRequest("id") === $"patient.entity.id", "studies")
    val withFamilyMemberHistory = joinAggregateList(withStudy, familyMemberHistory, withStudy("id") === $"patient.id", "familyMemberHistory")

    withFamilyMemberHistory.saveToEs("patient/patient", Map("es.mapping.id" -> "id"))

  }

}