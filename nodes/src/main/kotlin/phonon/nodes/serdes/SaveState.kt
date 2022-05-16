/**
 * Json save state with lazily created json string
 */

package phonon.nodes.serdes

interface JsonSaveState {
    // json string, lazily created
    public var jsonString: String?

    // create the json string
    public fun createJsonString(): String 

    // memoized access to json string
    public fun toJsonString(): String {
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