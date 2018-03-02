# OpenWhisk Mongo ArtifactStore

[![Build Status](https://travis-ci.org/chetanmeh/openwhisk-mongo.svg?branch=master)](https://travis-ci.org/chetanmeh/openwhisk-mongo)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![codecov](https://codecov.io/gh/chetanmeh/openwhisk-mongo/branch/master/graph/badge.svg)](https://codecov.io/gh/chetanmeh/openwhisk-mongo)

**Work in progress**

This repository provides a [MongoDB][1] based [ArtifactStore][2] implementation for [Apache OpenWhisk][3]. It uses
[MongoDB Scala Driver][8] for connecting to Mongo

## Usage

As its still in development you would need to perform some build steps locally

    # Clone OpenWhisk
    git clone --depth=1 https://github.com/apache/incubator-openwhisk.git openwhisk
    
    # Change directory to openwhisk and build
    cd openwhisk
    ./gradlew distDocker install
    
    # Export OPENWHISK_HOME
    export OPENWHISK_HOME="/path/to/openwhisk-repo"
    
    # Clone this repo
    git clone https://github.com/chetanmeh/openwhisk-mongo.git openwhisk-mongo
    
    # Change directory to checked out repo
    cd openwhisk-mongo
    ./gradlew distDocker 
    
    # Change to docker-compose
    cd docker-compose
    docker-compose --project-name openwhisk up
    
    # It would bring up the OpenWhisk setup locally
    
This should bring up the OpenWhisk setup with Controller and Invoker configured to use MongoDB
as storage store. This setup does not have the nginx configured so we need to hit the controller directly
Change the `~/.wskprops`

    AUTH=789c46b1-71f6-4ed5-8c54-816aa4f8c502:abczO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP
    APIHOST=http://localhost:8888
    
Further steps assume that `wsk` [CLI is configured on your setup][10]. Now try the steps [here][11] to see OpenWhisk in
action
  

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

Refer to [computed fields issues][9] for further details

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
[9]: https://github.com/chetanmeh/openwhisk-mongo/issues/8
[10]: https://github.com/apache/incubator-openwhisk/blob/master/docs/cli.md
[11]: https://github.com/apache/incubator-openwhisk/blob/master/docs/actions.md
