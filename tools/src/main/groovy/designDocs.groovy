import groovy.json.JsonSlurper

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


def owHomePath = args ? args[0] : System.getenv("OPENWHISK_HOME")
assert owHomePath : "OpenWhisk home cannot be determined from environment (via 'OPENWHISK_HOME') " +
        "or script arguments '-Popenwhisk.home``='"

File owDir = new File(owHomePath)

assert owDir.exists() : "OpenWhisk home directory ${owDir.absolutePath} does not exist"

File designDocDir = new File("$owHomePath/ansible/files")
File buildDir = createFreshBuildDir()


designDocDir.listFiles({it.name.endsWith(".json")} as FileFilter).each {File file ->
    def json = new JsonSlurper().parse(file)

    String id = json._id
    if (id && id.startsWith("_design/")){
        println "Processing ${file.name}"
        String baseName = id.substring("_design/".length())
        json.views?.each{String viewName, def view ->
            String viewJs = parseViewJs(view.map)
            File viewFile = new File(buildDir, "$baseName-${viewName}.js")
            viewFile.text = viewJs
            println "\t- ${viewFile.name}"
        }
    } else {
        println "Skipping ${file.name}"
    }
}
println "Generated view json files in ${buildDir.absolutePath}"

private static File createFreshBuildDir() {
    File dir = new File("build/designDocs")
    if (dir.exists()) {
        dir.deleteDir()
    }
    dir.mkdirs()
    dir
}

private static String parseViewJs(String jsonText) {
    jsonText = jsonText.replace("\\n", "")
    jsonText = jsonText.replace('\"','"')
    jsonText
}