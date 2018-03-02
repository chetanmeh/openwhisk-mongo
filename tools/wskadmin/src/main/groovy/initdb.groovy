/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.mongodb.client.model.Filters
import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.bson.Document


String uri = System.getProperty("mongo.uri")
assert uri : "Specify the Mongo url via 'mongo.uri' system property. Like '-Dmongo.uri=mongodb://localhost:27017'"

String dbValue = System.getProperty("mongo.db")
assert dbValue : "Specify the Mongo database to use via 'mongo.db' system property"

String owHome = args[0]

println "Connecting to $uri and database $dbValue"

MongoClient client = new MongoClient(new MongoClientURI(uri))
MongoDatabase db = client.getDatabase(dbValue)

File ansible = new File("$owHome/ansible/files")
File[] auths = ansible.listFiles({dir, name -> name.startsWith('auth.')} as FilenameFilter)

//def gmongo = new GMongoClient(new MongoClientURI(uri)).mongo.getDB(db)
MongoCollection<Document> subjects = db.getCollection("subjects")

auths.each {File file ->
    String authName = file.name.substring("auth.".length())
    Document d = subjects.find(Filters.eq("_id", authName)).first()
    if (d == null){
        def parts = file.text.trim().split(':')
        def auth = [
                _id : authName,
                _data : [
                        subject: authName,
                        namespaces : [[
                                name: authName,
                                uuid : parts[0],
                                key : parts[1]
                                ]
                        ]

                ]
        ]
        subjects.insertOne(new Document(auth))
        println("Created subject $authName")
    } else {
        println("Subject $authName already exists")
    }

}