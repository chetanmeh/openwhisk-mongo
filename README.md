# OpenWhisk Mongo ArtifactStore

**Work in progress**

This repository provides a [MongoDB][1] based [ArtifactStore][2] implementation for [Apache OpenWhisk][3]. It uses
[MongoDB Scala Driver][8] for connecting to Mongo

## Storage Model

OpenWhisk uses 3 databases in CouchDB to store subjects, whisks and activations. With MongoDB it would use a single 
database with 3 [collections][5]. 

### Conflict Handling

To ensure that concurrent updates of document do not accidentally override of changes OpenWhisk relies on [CouchDB MVCC][4] 
based [conflict management][6]. Each document has a `_rev` field managed by CouchDb to detect conflicts during concurrent
updates. 

As MongoDB does not support such a revision field by default we would rely on its conditional update to implement
conditional locking. This requires us to change the storage format. For example consider a following subject document

```json
{
  "_id": "guest",
  "_rev": "1-cd503abe708f3950bdc8c76c2cfccf12",
  "namespaces": [
    {
      "name": "guest",
      "key": "123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP",
      "uuid": "23bc46b1-71f6-4ed5-8c54-816aa4f8c502"
    }
  ],
  "subject": "guest"
}
```

This would be stored in following format

```json
{
  "_id": "guest",
  "_rev": 2,
  "_data": {
    "subject": "guest",
    "namespaces": [
      {
        "name": "guest",
        "uuid": "23bc46b1-71f6-4ed5-8c54-816aa4f8c502",
        "key": "123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP"
      }
    ]
  }
}
```

Here following transformations were done

1. `_rev` field is stored as integer and is [incremented][7] upon update of document 
2. `_data` - The actual whisk document is stored under `_data` field except `_rev` and `_id`

### Computed Fields

Some of the CouchDB views use computed fields for searching. To enable such usecases `MongoDbStore`
would compute such fields at time of creation itself and store them as a subdocument under `_computed`

```json
{
  "_id": "foo/bar/computedRule1",
  "_data": {
    "name": "computedRule1",
    "_computed": {
      "rootns": "foo"
    },
    "publish": false,
    "annotations": [],
    "version": "0.0.1"
  },
  "_rev": 1
}
```

Refer to GH-8 for further details

### Indexes 

TBD - How CouchDB views are mapped to Mongo indexes

[1]: https://www.mongodb.com/
[2]: https://github.com/apache/incubator-openwhisk/blob/master/common/scala/src/main/scala/whisk/core/database/ArtifactStore.scala
[3]: http://openwhisk.incubator.apache.org/
[4]: http://guide.couchdb.org/draft/consistency.html#locking
[5]: https://docs.mongodb.com/manual/reference/glossary/#term-collection
[6]: http://guide.couchdb.org/draft/conflicts.html
[7]: https://docs.mongodb.com/manual/reference/operator/update/inc/
[8]: http://mongodb.github.io/mongo-scala-driver/