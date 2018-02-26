
1. Review usage of dates in entity
2. ArtifactStore#shutdown is not called in production code

Subjects
--------

- Index on subject
- Index on namespace.name
- Index on uuid, key, !blocked
- Index on namespace.uuid and namespace.key

Projections
-----------

Whisks
======
- Index on type, namespace, updated
- Index on type, rootns, updated
- Index on root, updated - if type = packages, actions, triggers, rules

Projections
-----------

action          - namespace, name, version, publish, annotations, updated, limits, doc.exec.binary, 
packages        - namespace, name, version, publish, annotations, updated, binding
packages-public - namespace, name, version, publish, annotations, updated, binding = false
rules           - _id
triggers        - namespace, name, version, publish, annotations, updated

Entity Queries
--------------
WhiskEntityQueries#listAllInNamespace
  val startKey = List(namespace.asString)
  val endKey = List(namespace.asString, TOP)
  reduce= false
  descending = true
  

WhiskEntityQueries#listCollectionInNamespace
  -  val startKey = List(path.asString, since map { _.toEpochMilli } getOrElse 0)
     val endKey = List(path.asString, upto map { _.toEpochMilli } getOrElse TOP, TOP)
  - reduce = false
WhiskActivation#listActivationsMatchingName
  - val startKey = List(namespace.addPath(path).asString, since map { _.toEpochMilli } getOrElse 0)
    val endKey = List(namespace.addPath(path).asString, upto map { _.toEpochMilli } getOrElse TOP, TOP)
  - reduce = false
  - whisks-filters.v2.1.0-activations.js
  WhiskEntityQueries#queries
    - descending = true


Activations
===========

Created in SequenceActions

- Index on namespace, start
- Index on start -> Used for cleanup (byDate) activationId != null
- byDateWithLogs - TODO - Non sequence activations
- Index on namespace/path(), start

Projections
-----------

activations - namespace, name, version, publish, annotations, activationId, start, end, cause, response.statusCode



Query
-----

- startKey = endKey -> Do by equals
- if includeDocs = true then just transform

To investigate
--------------

PrimitiveActions.invokeSingleAction - Says params are merged with action params superceding passed param
