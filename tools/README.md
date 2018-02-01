# Script to dump design docs

This module provides script to dump the design docs js in readable format. It reads all the design docs from
_<OPENWHISH_HOME>/ansibles/files_ and dumps them in _build/designDocs_ directory.

If home is set via environment variable `OPENWHISK_HOME` then just execute it via

    ../gradlew -q
    
Or you can pass the home via

    ../gradlew -q -Popenwhisk.home=/path/to/home
    
Sample output

    Processing whisks_design_document_for_entities_db_v2.1.0.json
            - whisks.v2.1.0-rules.js
            - whisks.v2.1.0-packages-public.js
            - whisks.v2.1.0-packages.js
            - whisks.v2.1.0-actions.js
            - whisks.v2.1.0-triggers.js
    Processing activations_design_document_for_activations_db.json
            - activations-byDate.js
    Processing auth_index.json
            - subjects-identities.js
    Processing filter_design_document.json
    Processing whisks_design_document_for_activations_db_v2.1.0.json
            - whisks.v2.1.0-activations.js
    Skipping runtimes.json
    Processing logCleanup_design_document_for_activations_db.json
            - logCleanup-byDateWithLogs.js
    Processing whisks_design_document_for_all_entities_db_v2.1.0.json
            - all-whisks.v2.1.0-all.js
    Processing whisks_design_document_for_activations_db_filters_v2.1.0.json
            - whisks-filters.v2.1.0-activations.js
    Generated view json files in /path/too/tools/build/designDocs
