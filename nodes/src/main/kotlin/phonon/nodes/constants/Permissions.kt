/**
 * Enum town permission groups
 */

package phonon.nodes.constants

enum class PermissionsGroup {
    TOWN,        // any member of town
    NATION,      // any member of nation
    ALLY,        // any member of allied towns
    OUTSIDER,    // anyone
    TRUSTED;     // trusted member in town

    companion object {
        val values: Array<PermissionsGroup> = PermissionsGroup.values()
    }
}

// town permissions categories
enum class TownPermissions {
    INTERACT,
    BUILD,
    DESTROY,
    CHESTS,
    USE_ITEMS,
    INCOME;

    companion object {
        val values: Array<TownPermissions> = TownPermissions.values()
    }
}