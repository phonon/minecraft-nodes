/**
 * Json save state with lazily created json string
 */

package phonon.nodes.serdes

interface JsonSaveState {
    // json string, lazily created
    var jsonString: String?

    // create the json string
    fun createJsonString(): String

    // memoized access to json string
    fun toJsonString(): String {
        val jsonString = this.jsonString
        if ( jsonString === null ) {
            val json = this.createJsonString()
            this.jsonString = json
            return json
        }
        else {
            return jsonString
        }
    }
}