/**
 * Put constants in here.
 * Issues is that namespaced constants in `Nodes` object are not
 * recognized by compiler.
 */

// Resident ranks while rendering player list for a town.
// This determines player list order and icon identifier.
export const RESIDENT_RANK_NONE = 0;
export const RESIDENT_RANK_OFFICER = 1;
export const RESIDENT_RANK_LEADER = 2;

// Constant enum for rendering town name tags on map.
export const RENDER_TOWN_NAMETAG_NONE = 0;   // don't render town name tag
export const RENDER_TOWN_NAMETAG_TOWN = 1;   // render town name
export const RENDER_TOWN_NAMETAG_NATION = 2; // render town nation name 
