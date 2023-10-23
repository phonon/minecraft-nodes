/**
 * nodes.js
 * --------------------------------------------------
 * TODO: better comments + consistent style
 */

"use strict";

// require polyfill
import "@babel/polyfill";
import { createRoot } from "react-dom/client";

import { saveAs } from "file-saver";
import { v4 as uuidv4 } from "uuid";

import {
    RESIDENT_RANK_NONE, RESIDENT_RANK_OFFICER, RESIDENT_RANK_LEADER,
    RENDER_TOWN_NAMETAG_NONE, RENDER_TOWN_NAMETAG_TOWN, RENDER_TOWN_NAMETAG_NATION,
    TownSortKey,
} from "./constants.js";
import IconMapCapital from "assets/icon/icon-map-capital.svg";
import { StripePattern } from "ui/stripe-pattern.jsx";
import { Editor } from "editor/editor.jsx";
import { WorldRenderer } from "world/world.jsx";
import { Territory } from "world/territory.jsx";
import { Port, PortTooltip } from "world/port.jsx";

import { World, IndexSampler } from "wasm_main";

/**
 * Required format properties for nodes resources.
 * Note: resources are fully configurable objects (so in future other
 * libraries can extend definitions in resources). So, the format
 * is left unfrozen here.
 */
class NodesResource {
    static defaultProps = Object.freeze({
        name: undefined,
        icon: null,
        cost: {
            scale: 1.0,
            constant: 0,
        },
        priority: 0,
    });
    
    constructor(options = {}) {
        Object.assign(this, NodesResource.defaultProps, options);
    }

    /**
     * Return game compatible object for json serialization.
     * For resources, return everything (allow full customization in 
     * editor).
     */
    export() {
        return this;
    }
}

/**
 * Data format for nodes territories.
 * NOTE: this is a frontend wrapper for drawing, actual chunks data is
 * stored in territory object handled in wasm.
 */
class NodesTerritory {
    static defaultProps = Object.freeze({
        id: undefined,
        name: "",                // territory readable name identifier
        core: undefined,         // {x: x, y: y}
        coreChunk: undefined,    // {x: x, y: y}
        borders: undefined,      // borders for rendering
        size: 0,                 // chunks.length
        neighbors: [],           // neighboring ids
        isEdge: false,           // territory borders wilderness
        nodes: [],               // array of node type names
        terrElement: undefined,  // react jsx element to render
        town: undefined,         // link to town object
        occupier: undefined,     // link to occupying town object
        color: undefined,        // integer id to a color index, used by client
        cost: 0,                 // territory power cost

        // editor internal variables
        selected: false,
    });

    constructor(id) {
        Object.assign(this, NodesTerritory.defaultProps, {
            id: id,
        });
        Object.seal(this);
    }

    /**
     * TODO
     */
    export() {
        return this;
    }
}

/**
 * Nodes resident object data format, holds resident uuid and rank.
 * For rank format just use pre-defined magic number ints,
 * defined in `Nodes` object.
 * 
 * Leave this unsealed so that ingame specific properties are retained
 * and exported.
 */
class NodesResident {
    // required properties
    static defaultProps = Object.freeze({
        uuid: undefined,
        name: "Anonymous",
        prefix: "",
        suffix: "",
        town: undefined,
        nation: undefined,
        rank: RESIDENT_RANK_NONE,
    });

    constructor(options = {}) {
        Object.assign(this, NodesResident.defaultProps, options);

        // if uuid is undefined, generate new random uuid
        this.uuid = this.uuid ?? uuidv4();
    }

    /**
     * Return everything for export. This may contain in-game specific
     * properties appended to editor required properties.
     */
    export() {
        return this;
    }
}

/**
 * Format for a nodes Town object.
 */
class NodesTown {
    static defaultProps = Object.freeze({
        uuid: undefined,
        name: undefined,
        color: [255, 255, 255],
        colorTown: [255, 255, 255],
        colorNation: [255, 255, 255],

        // flag that anyone can join
        open: false,

        // residents
        leader: undefined,
        playerNames: [],
        residents: [],
        residentUuids: [],

        // territories
        territories: [],
        annexed: [],
        captured: [],
        home: -1,
        spawn: [0.0, 0.0, 0.0],
        
        // relations with other towns
        allies: [],
        enemies: [],
        truce: [],

        // nation
        nation: undefined,
    });

    constructor(options = {}) {
        Object.assign(this, NodesTown.defaultProps, options);
        this.original = options; // store copy of original, for export
        Object.seal(this);

        // if uuid is undefined, generate new random uuid
        this.uuid = this.uuid ?? uuidv4();
    }

    /**
     * Return in-game plugin compatible town data object.
     * Returns original object which may contain in-game specific 
     * properties not used in the editor. Then overwrite original
     * with exporter editable properties.
     */
    export() {
        return Object.assign({}, this.original, {
            uuid: this.uuid,
            color: this.colorTown,
            open: this.open,
            leader: this.leader,
            residents: this.residentUuids,
            territories: this.territories,
            annexed: this.annexed,
            captured: this.captured,
            home: this.home,
            spawn: this.spawn,
            allies: this.allies,
            enemies: this.enemies,
            truce: this.truce,
            income: this.income,
            incomeEgg: this.incomeEgg,
            claimsBonus: this.claimsBonus,
            claimsPenalty: this.claimsPenalty,
        });
    }
}

/**
 * Data format for nodes nation objects.
 */
class NodesNation {
    static defaultProps = Object.freeze({
        uuid: undefined,
        capital: undefined,
        color: [255, 255, 255],
        towns: [],
        allies: [],
        enemies: [],

        // editor state
        numPlayers: 0,
        numTerritories: 0,
    });

    constructor(options = {}) {
        Object.assign(this, NodesNation.defaultProps, options);
        this.original = options;
        Object.seal(this);

        // if uuid is undefined, generate new random uuid
        this.uuid = this.uuid ?? uuidv4();
    }

    /**
     * Return in-game plugin compatible nation data object.
     * Return original object overwritten with editor editable
     * properties.
     */
    export() {
        return Object.assign({}, this.original, {
            uuid: this.uuid,
            capital: this.capital,
            color: this.color,
            towns: this.towns,
            allies: this.allies,
            enemies: this.enemies,   
        });
    }
}

/**
 * Data format for nodes port objects.
 */
class NodesPort {
    constructor(name, group, x, z) {
        this.name = name;
        this.group = group;
        this.groupsString = group.join(", ");
        this.x = x;
        this.z = z;

        Object.seal(this);
    }
}


// painting event handlers
const handleWindowMouseUp = (e) => {
    Nodes._stopPaint();
    window.removeEventListener("mouseup", handleWindowMouseUp);
};

const handleMouseDown = (e) => {
    e.preventDefault();
    // e.stopPropagation(); // problem: nodes pane is overlaid on top, prevents dragging map
    
    if ( e.button === 2 ) { // right click only
        Nodes._startPaint();
    
        // add window event for mouse up
        window.addEventListener("mouseup", handleWindowMouseUp);
    }
};

const handleMouseUp = (e) => {
    Nodes._stopPaint();
    window.removeEventListener("mouseup", handleWindowMouseUp);
}

// private settings
let editorEnabled = true; // constant for enabling editor panels and territory painting
let uploadEnabled = true; // constant for enabling uploading files


// Custom leaflet.js renderer layer used to forward map size parameters
// to the custom react svg layer. Main functions are `_updateTransform`
// and `_update`. These mimic `L.Renderer` and `L.SVG` objects.
// Essentially re-create those functionality that sets SVG position
// and viewbox when map moved, then forward parameters to the react
// svg layer. This is pretty dirty/hacky. Make sure to understand `L.SVG`
// before updating this.
const NodesSvgRenderer = L.Layer.extend({

    // @section
    // @aka Renderer options
    options: {
        pane: "nodesPane",

        // @option padding: Number = 0.1
        // How much to extend the clip area around the map view (relative to its size)
        // e.g. 0.1 would be 10% of map view in each direction
        // Increases buffer size of nodes map for panning.
        padding: 0.25,
    },

    initialize: function (setTransform, options) {
        L.Util.setOptions(this, options);
        L.Util.stamp(this);
        this._layers = this._layers || {};
        this._setTransform = setTransform;
    },

    onAdd: function () {        
        // set initial center and zoom
        this._center = this._map.getCenter();
        this._zoom = this._map.getZoom();
        
        // set initial bounds
        const p = this.options.padding;
        const mapSize = this._map.getSize();
        const min = this._map.containerPointToLayerPoint(mapSize.multiplyBy(-p)).round();
        this._bounds = new L.Bounds(min, min.add(mapSize.multiplyBy(1 + p * 2)).round());

        this._updateTransform(this._center, this._zoom);
        this._update();
    },

    onRemove: function () {
        this.off("update", this._updatePaths, this);
    },

    getEvents: function () {
        var events = {
            viewreset: this._reset,
            zoom: this._onZoom,
            moveend: this._update,
            zoomend: this._onZoomEnd
        };
        if (this._zoomAnimated) {
            events.zoomanim = this._onAnimZoom;
        }
        return events;
    },

    _reset: function () {
        this._update();
        this._updateTransform(this._center, this._zoom);
    },

    _onAnimZoom: function (ev) {
        this._updateTransform(ev.center, ev.zoom);
    },

    _onZoom: function () {
        this._updateTransform(this._map.getCenter(), this._map.getZoom());
    },

    _onZoomStart: function () { },

    _onZoomEnd: function () { },
    
    // Set origin topLeftOffset from new center and zoom
    _updateTransform: function (center, zoom) {
        const scale = this._map.getZoomScale(zoom, this._zoom);
        const viewHalf = this._map.getSize().multiplyBy(0.5 + this.options.padding);
        const currentCenterPoint = this._map.project(this._center, zoom);
        const destCenterPoint = this._map.project(center, zoom);
        const centerOffset = destCenterPoint.subtract(currentCenterPoint);

        this._topLeftOffset = viewHalf.multiplyBy(-scale).add(viewHalf).subtract(centerOffset);
        this._setContainerTransformString(scale);

        // set new zoom and center
        this._zoom = zoom;
        this._center = center;
    },
    
    // Set svg container transform string. This is forwarded to react svg element
    _setContainerTransformString: function (scale) {
        const pos = this._topLeftOffset.add(this._bounds.min);

        this._transformString = (L.Browser.ie3d ?
            "translate(" + pos.x + "px," + pos.y + "px)" :
            "translate3d(" + pos.x + "px," + pos.y + "px,0)") +
        (scale ? " scale(" + scale + ")" : "");
    },

    // Runs every time map is panned or zoomed.
    _update: function () {
        if (this._map._animatingZoom && this._bounds) { return; }

        // Update pixel bounds of renderer container (for positioning/sizing/clipping later)
        // Subclasses are responsible of firing the 'update' event.
        const p = this.options.padding;
        const mapSize = this._map.getSize();
        const min = this._map.containerPointToLayerPoint(mapSize.multiplyBy(-p)).round();
        const bounds = new L.Bounds(min, min.add(mapSize.multiplyBy(1 + p * 2)).round());
        
        // update center, zoom, bounds
        this._center = this._map.getCenter();
        this._zoom = this._map.getZoom();
        this._bounds = bounds;

        const boundsSize = bounds.getSize();
        const width = boundsSize.x;
        const height = boundsSize.y;

          // movement: update container viewBox so that we don't have to change
        // coordinates of individual layers
        const viewbox = [bounds.min.x, bounds.min.y, boundsSize.x, boundsSize.y].join(" ");

        // const transformString = L.DomUtil.getTranslateString(b.min);
        this._setContainerTransformString();

        // console.log(width, height, viewbox, this._transformString, this._map.getZoom());
        
        this._setTransform(width, height, viewbox, this._transformString, this._map.getZoom());

        this.fire("update");
    },
});

/**
 * Global container for nodes functions
 */
const Nodes = {

    // versioning TODO
    version: {
        major: 0,
        minor: 1,
        patch: 0,
    },
    
    // map of player resident objects
    // uuid String => resident
    residents: new Map(),

    // global world data containers
    // name => town
    towns: new Map(),
    townsList: [], // sorted list of town objects

    // nations container
    // name => nation
    nations: new Map(),

    // id => territory
    territories: new Map(),
    territoryIdToIndex: new Map(),  // map id -> map order index

    // name => node
    nodes: new Map(),
    nodesNameList: [], // array of nodes names, for optimizing react rendering

    // name => port
    ports: new Map(),
    portsJsx: [], // port rendered jsx, never changes

    // town capital icon rendered jsx elements
    townCapitalElementsJsx: [],

    // town names rendered jsx elements
    townNameElementsJsx: [],

    // resource icon name => icon url
    resourceIcons: new Map(),
    defaultResourceIconFiles: [      // default .json files to load resource icon map from,
        "nodes/resource_icons.json", // files should contain keys "icon_name": "urlpath/to/resource_icon.png"
    ],                               // this will load each file and merge results in order

    // global territory cost parameters
    territoryCost: {
        scale: 1.0,
        constant: 10
    },

    // default node custom properties setting (can be configured)
    defaultNodeProperties: {
        income: {},
        ore: {},
        crops: {},
        animals: {},
    },

    // default string for an empty node, used by nodes ui panel as placeholder
    defaultNodeString: JSON.stringify(Object.assign(new NodesResource({ name: "null" }), {
        income: {},
        ore: {},
        crops: {},
        animals: {},
    }),
        null,
        3,
    ),

    // wasm shadow realm handles actual territory chunk editing
    wasmWorld: new World(
        16,   // world chunk to coord scale
    ),

    // dynmap hook
    dynmap: undefined,

    // hook to custom dynmap overlay pane svg container
    dynmapSvgContainer: undefined, // dom element
    dynmapSvgContainerRoot: undefined, // react root

    // editor container
    editorContainer: undefined, // dom element
    editorContainerRoot: undefined, // react root

    // port tooltip container:
    portTooltipContainer: undefined, // dom element
    portTooltipContainerRoot: undefined, // react root

    // GLOBAL STATE
    // user cursor in editor
    screenX: 0.0, // x in dom screen
    screenY: 0.0, // y in dom screen
    cursorX: 0.0, // x in map space
    cursorY: 0.0, // y in map space
    chunkX: 0.0,
    chunkY: 0.0,
    cursorLatLng: {lat: 0, lng: 0},
    selectedNodeName: undefined,
    selectedNodeIndex: undefined,
    selectedTerritory: undefined,   // main selected territory (for paint operations)
    selectedTerritories: new Map(), // multiple selected territories, for multi-territory operations
    selectedTown: undefined,
    selectedTownIndex: undefined,
    shiftKey: false, // detect shift key down
    ctrlKey: false,  // detect ctrl key down
    isPainting: false,
    paintRadius: 2.0, // size of paint cursor
    isEditingTerritoryNodes: false, // allow assign/remove nodes with ctrl + click select 
    // paint settings
    minPaintRadius: 1.0,  // min brush radius
    maxPaintRadius: 10.0, // max brush radius
    // towns list sort key
    townsSortKey: TownSortKey.TERRITORIES,

    // random generation settings
    generatorAverageRadius: 4.0,
    generatorScaleX: 1.0,
    generatorScaleY: 1.0,
    generatorRandomSeed: 0,
    generatorIterationsSmoothCenters: 2,
    generatorIterationsSmoothCorners: 2,
    generatorDeleteSmallerThan: 0,
    generatorMergeSmallerThan: 0,
    generatorCopyName: true,

    // random resource placement settings
    resourceDistributeRandomSeed: 0,
    resourceDistributeSettings: {},
    
    // map rendering state
    mapWidth: 0,
    mapHeight: 0,
    mapViewbox: "",
    mapTransform: "",
    mapZoom: 0,
    territoryElements: [], // pre-calculated array of territory jsx to render
    stripePatterns: [],    // pre-calculated array of stripe patterns for occupied territories
    backgroundImageSrc: undefined, // background image origin
    backgroundImageName: "", // background image readable name (e.g. for local file uploads)
    backgroundImageOriginX: 0,
    backgroundImageOriginY: 0,
    backgroundImageEndX: 0,
    backgroundImageEndY: 0,
    backgroundImageWidth: 0,
    backgroundImageHeight: 0,

    // territory render options:
    renderTerritoryIcons: true,      // render resource icons
    renderTerritoryId: false,        // render territory ids
    renderTerritoryCost: false,      // render cost number
    renderTerritoryOpaque: false,    // render ~opaque solid town/nation colors
    renderTerritoryNoBorders: false, // don't render territory borders
    renderTerritoryCapitals: false,  // render capital markers
    renderTerritoryColors: false,    // for debugging, render territory assigned colors
    
    // render town names (enum value, set to constant RENDER_TOWN_NAMETAG_*)
    renderTownNames: 0,               // render town names


    initialize: (options, callback) => {
        Nodes.editorContainer = options.container;
        Nodes.editorContainerRoot = createRoot(options.container);
        Nodes.portTooltipContainer = options.portTooltipContainer;
        Nodes.portTooltipContainerRoot = createRoot(options.portTooltipContainer);

        // world background image config
        if ( options.backgroundImage !== undefined ) {
            Nodes.backgroundImageSrc = options.backgroundImage;
            Nodes.backgroundImageName = options.backgroundImage;
        }
        if ( options.backgroundImageOriginX !== undefined ) {
            Nodes.backgroundImageOriginX = options.backgroundImageOriginX;
        }
        if ( options.backgroundImageOriginY !== undefined ) {
            Nodes.backgroundImageOriginY = options.backgroundImageOriginX;
        }
        if ( options.backgroundImageWidth !== undefined ) {
            Nodes.backgroundImageWidth = options.backgroundImageWidth;
            Nodes.backgroundImageEndX = Nodes.backgroundImageOriginX + Nodes.backgroundImageWidth;
        }
        if ( options.backgroundImageHeight !== undefined ) {
            Nodes.backgroundImageHeight = options.backgroundImageHeight;
            Nodes.backgroundImageEndY = Nodes.backgroundImageOriginY + Nodes.backgroundImageHeight;
        }

        // paint radius
        if ( options.minPaintRadius !== undefined ) {
            Nodes.minPaintRadius = options.minPaintRadius;
        }
        if ( options.maxPaintRadius !== undefined ) {
            Nodes.maxPaintRadius = options.maxPaintRadius;
        }

        // get editor enabled setting
        if ( options.editor !== undefined && options.editor === false ) {
            editorEnabled = false;
        }

        // upload file enable setting
        if ( options.uploadEnabled !== undefined && options.uploadEnabled === false ) {
            uploadEnabled = false;
        }
        
        // territory render options
        Nodes.renderTerritoryIcons = options.renderTerritoryIcons ?? Nodes.renderTerritoryIcons;     
        Nodes.renderTerritoryId = options.renderTerritoryId ?? Nodes.renderTerritoryId;       
        Nodes.renderTerritoryCost = options.renderTerritoryCost ?? Nodes.renderTerritoryCost;     
        Nodes.renderTerritoryOpaque = options.renderTerritoryOpaque ?? Nodes.renderTerritoryOpaque;   
        Nodes.renderTerritoryNoBorders = options.renderTerritoryNoBorders ?? Nodes.renderTerritoryNoBorders;
        Nodes.renderTerritoryCapitals = options.renderTerritoryCapitals ?? Nodes.renderTerritoryCapitals; 
        Nodes.renderTerritoryColors = options.renderTerritoryColors ?? Nodes.renderTerritoryColors;   

        let dynmap = options.dynmap;
        Nodes.renderEditor();
        Nodes.dynmap = dynmap;

        // add custom hook for mouse move
        Nodes.dynmap.map.on("mousemove", Nodes.handleMouseMove);

        // create custom map pane and layer for nodes
        let customOverlayPane = dynmap.map.createPane("nodesPane");
        customOverlayPane.id = "nodes-svg-renderer";

        let layerNodesRenderer = new NodesSvgRenderer(Nodes.setMapTransform, {});
        layerNodesRenderer.addTo(dynmap.map);
        Nodes.dynmapSvgContainer = customOverlayPane;
        Nodes.dynmapSvgContainerRoot = createRoot(customOverlayPane);

        // key detect handler
        // NOTE: does not work for iframe region
        if ( editorEnabled === true ) {
            window.addEventListener("keydown", e => {
                if ( e.key === "Shift" ) {
                    if ( Nodes.shiftKey === false ) {
                        Nodes.shiftKey = true;
                        Nodes.renderWorld();
                    }
                }
                else if ( e.key === "Control" ) {
                    if ( Nodes.ctrlKey === false ) {
                        Nodes.ctrlKey = true;
                        Nodes.renderWorld();
                    }
                }
                // spacebar: toggle painting
                else if ( e.key === " " ) {
                    Nodes._togglePainting();
                }
                // creating new territory while painting
                else if ( e.key === "a" && Nodes.enabledPainting ) {
                    Nodes._createTerritory();
                }
            });
        }
        else {
            window.addEventListener("keydown", e => {
                if ( e.key === "Shift" ) {
                    if ( Nodes.shiftKey === false ) {
                        Nodes.shiftKey = true;
                        Nodes.renderWorld();
                    }
                }
                else if ( e.key === "Control" ) {
                    if ( Nodes.ctrlKey === false ) {
                        Nodes.ctrlKey = true;
                        Nodes.renderWorld();
                    }
                }
            });
        }

        window.addEventListener("keyup", e => {
            if ( e.key === "Shift" ) {
                if ( Nodes.shiftKey === true ) {
                    Nodes.shiftKey = false;
                    Nodes.renderWorld();
                }
            }
            else if ( e.key === "Control" ) {
                if ( Nodes.ctrlKey === true ) {
                    Nodes.ctrlKey = false;
                    Nodes.renderWorld();
                }
            }
        });

        // 1. load editor config files:
        //   1. resource icon json files
        Nodes.loadResourceIcons(options.resourceIcons)
        .then(() => {
            // 2. load nodes world config files in order:
            //   1. load global territory costs, options: config.json
            //   2. load nodes: world.json
            //   3. load towns: towns.json
            //   4. load ports if exists: ports.json
            // TODO: check if fetch response is VALID then load
            // otherwise loads invalid files
            // Options on loadFile are `loadFile(path, mergeFile=false, render=false)`
            // in-order to defer first render until all files are loaded
            if ( options.world !== undefined ) {
                Nodes.loadFile(options.config, false, false)
                .then(() => {
                    return Nodes.loadFile(options.world, false, false);
                })
                .then(() => {
                    return Nodes.loadFile(options.towns, false, false);
                })
                .then(() => {
                    return Nodes.loadFile(options.ports, false, false);
                })
                .then(() => {
                    // update render
                    Nodes._updateStripePatterns();
                    Nodes._calculateAllTerritoryCosts();

                    // update territories
                    Nodes._updateAllTerritoryElements();

                    // render
                    Nodes.renderEditor();
                    Nodes.renderWorld();
                });
            }
            else { // just re-render editor to make sure icons load
                Nodes.renderEditor();
            }
        })
        .catch(err => { console.error(err) });
        
        if ( callback !== undefined ) {
            callback();
        }
    },

    // ============================================
    // Load functions
    // ============================================
    
    /**
     * Load resource icons mappings from array of .json files.
     * Each .json file should contain a single object with keys
     * mapping a resource icon name to url to image file:
     * ```
     *    {
     *       "gold": "images/nodes/gold.png",
     *       "diamond": "images/nodes/diamond.png",
     *    }
     * ```
     * This will load an array of paths to icon source mapping files,
     * ["path/to/file1.json", "path/to/file2.json", ...]
     * and merge results of each icon mapping in order.
     */
    loadResourceIcons: (resourceIconJsonPaths = Nodes.defaultResourceIconFiles) => {
        let loadingFiles = resourceIconJsonPaths.map(path => new Promise((resolve, reject) => {
            fetch(path)
            .then(response => {
                if ( response.ok ) {
                    resolve(response.json());
                }
                else {
                    reject(`Failed to load resource icon file: '${path}'`);
                }
            })
            .catch(err => reject(err));
        }));
        
        return Promise.all(loadingFiles)
            .then(resourceIconsMaps => {
                resourceIconsMaps.forEach(r => {
                    Object.keys(r).forEach(icon => Nodes.resourceIcons.set(icon, r[icon]));
                })
            });
    },

    /**
     * Wrapper for loading different Nodes world config files.
     * Forwards loaded .json into Nodes.load after fetch.
     */
    loadFile: (path, mergeFile = false, render = true) => {
        if ( path === undefined ) {
            return Promise.resolve()
        }

        return new Promise((resolve, reject) => {
            fetch(path)
            .then(response => {
                if ( response.ok ) {
                    return response.json();
                }
                else {
                    reject();
                }
            })
            .then(json => {
                if ( json !== undefined ) {
                    try {
                        Nodes.load(json, mergeFile, render);
                        resolve();
                    } catch ( err ) {
                        console.error(err);
                        reject();
                    }
                }
                else {
                    reject();
                }
            });
        });
    },
    
    /**
     * Load world data from parsed .json object. This handles loading
     * all different nodes world config file types, indicated in
     * .json file "meta.type" field:
     * - global config options (config.json)
     * - nodes world/territories data (world.json)
     * - towns data (towns.json)
     * - ports nodes plugin data (ports.json)
     * 
     * If `mergeFile = false`, the world or towns data will be
     * cleared before loading new data (so data starts fresh).
     * Else, data is merged and overwrites existing keys in 
     * the world storage maps.
     * 
     * `render` determines if world/editor should be re-rendered
     * after files are loaded.
     */
    load: (data, mergeFile = false, render = true) => {
        // global config options
        if ( data.meta?.type === "config" ) {
            // get global cost
            if ( data.territoryCost !== undefined ) {
                Nodes.territoryCost.scale = data.territoryCost.scale;
                Nodes.territoryCost.constant = data.territoryCost.constant;
                // TODO: re-calculate all costs? Only necessary if costs
                // are already displayed on territories...so needed for merging
                // new config file...
            }
        }
        // world definition file (territories + nodes)
        else if ( data.meta?.type === "world" ) {
            // clear existing data
            if ( mergeFile === false ) {
                Nodes._clearWorld();

                // unselect any node and territory
                Nodes.selectedNodeName = undefined;
                Nodes.selectedNodeIndex = undefined;
                Nodes.selectedTerritory = undefined;
                Nodes.selectedTown = undefined;
                Nodes.selectedTownIndex = undefined;
            }

            // parse node types
            if ( data.nodes !== undefined ) {
                Object.keys(data.nodes).forEach(name => {
                    // create new node, assign keys saved
                    const node = Object.assign(new NodesResource({ name }), data.nodes[name]);

                    Nodes.nodes.set(name, node);
                    Nodes.nodesNameList.push(name);
                });

                // create new array so editor resource list will re render
                Nodes.nodesNameList = Nodes.nodesNameList.slice();
            }
            
            // parse and load territories
            if ( data.territories !== undefined ) {
                // first pass iterate to find max id
                let maxId = Nodes.wasmWorld.getTerritoryIdCounter();
                for ( const idKey of Object.keys(data.territories) ) {
                    const id = parseInt(idKey);
                    if ( id > maxId ) {
                        maxId = id;
                    }
                }
                Nodes.wasmWorld.setTerritoryIdCounter(maxId + 1);

                // start index of map = current size
                const startIndex = Nodes.territories.size;

                // create new territories
                Object.keys(data.territories).forEach((idKey, index) => {
                    let id = parseInt(idKey);
                    if ( Nodes.territories.has(id) ) { // use new id if already taken
                        id = Nodes.wasmWorld.getNewTerritoryId();
                    }

                    const terr = data.territories[idKey];
                    const newTerritory = new NodesTerritory(id);
                    Nodes.territories.set(id, newTerritory);
                    Nodes.territoryIdToIndex.set(id, startIndex + index);

                    // create territory in wasm shadow realm
                    Nodes.wasmWorld.createTerritory(id);
                    Nodes.wasmWorld.removeCoords(terr.chunks);
                    Nodes.wasmWorld.addCoordsToTerritory(id, terr.chunks);

                    // set name
                    newTerritory.name = terr.name;
                    
                    // set core
                    newTerritory.core = {x: terr.core[0], y: terr.core[1]};
                    newTerritory.coreChunk = {x: terr.coreChunk[0], y: terr.coreChunk[1]};

                    // set nodes
                    newTerritory.nodes = terr.nodes;
                });

                // force re-calculate size + border for all territories
                for ( const [id, terr] of Nodes.territories.entries() ) {
                    terr.size = Nodes.wasmWorld.getTerritorySize(id);
                    Nodes._getTerritoryBorder(id); // update border
                }

                // update render
                Nodes._updateStripePatterns();
                Nodes._calculateAllTerritoryCosts();

                // update territories
                Nodes._updateAllTerritoryElements();

                // update town capital elements
                Nodes._updateAllTownCapitalsJsx();

                // update town name tags
                Nodes._updateAllTownNameTagJsx();
            }

            if ( render ) {
                Nodes.renderEditor();
                Nodes.renderWorld();
            }
        }
        // ==================================================
        // towns definition file (player created towns, ...)
        // ==================================================
        else if ( data.meta?.type === "towns" ) {
            Nodes._clearTowns();
            
            // parse residents
            if ( data.residents !== undefined && data.residents !== null ) {
                Object.keys(data.residents).forEach(uuid => {
                    Nodes.residents.set(uuid, data.residents[uuid]);
                });
            }

            // parse towns
            if ( data.towns !== undefined && data.towns !== null ) {
                Object.keys(data.towns).forEach(name => {
                    const town = new NodesTown({
                        name: name,
                        ...data.towns[name],
                    });

                    // map each resident UUID into a resident object
                    // if resident does not exist, create a new "Anonymous" resident
                    town.residents = town.residents.map(uuid => {
                        return new NodesResident({
                            uuid: uuid,
                            name: Nodes.residents.get(uuid)?.name ?? "Anonymous",
                            rank: RESIDENT_RANK_NONE,    
                        });
                    })
                    town.residentUuids = town.residents.map(r => r.uuid);
                    
                    // pre-computed list of each resident's name
                    town.playerNames = town.residents.map(r => {
                        return r.name; 
                    });

                    Nodes.towns.set(name, town);

                    // make links claims -> town
                    town.territories.forEach(id => {
                        if ( Nodes.territories.has(id) ) {
                            Nodes.territories.get(id).town = town;
                        }
                    });

                    // make links occupied territory -> town
                    town.captured.forEach(id => {
                        if ( Nodes.territories.has(id) ) {
                            Nodes.territories.get(id).occupier = town;
                        }
                    });

                    // save specific town color and nation color
                    town.colorTown = town.color;
                    town.colorNation = town.color;
                });
            }

            // parse nations
            if ( data.nations !== undefined && data.nations !== null ) {
                Object.keys(data.nations).forEach(name => {
                    const nation = new NodesNation(data.nations[name]);
                    Nodes.nations.set(name, nation);
    
                    // link towns to nation
                    nation.towns.forEach(t => {
                        if ( Nodes.towns.has(t) ) {
                            const town = Nodes.towns.get(t);
                            town.nation = name;
                            town.color = nation.color;
                            town.colorNation = nation.color;
                        }
                    });
                });
            }

            // sort towns and residents
            Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
            for ( const town of Nodes.townsList ) {
                town.residents = Nodes._sortTownResidents(town);
            }

            // update render
            Nodes._updateStripePatterns();

            // update territories
            Nodes._updateAllTerritoryElements();

            // update town capital elements
            Nodes._updateAllTownCapitalsJsx();

            // update town name tags
            Nodes._updateAllTownNameTagJsx();

            if ( render ) {
                Nodes.renderEditor();
                Nodes.renderWorld();
            }
        }
        // ports extension
        else if ( data.meta?.type === "ports" ) {
            Nodes._clearPorts();

            // parse names
            for ( const name of Object.keys(data.ports) ) {
                const portData = data.ports[name];
                if ( !portData.hasOwnProperty("groups") || !portData.hasOwnProperty("x") || !portData.hasOwnProperty("z") ) {
                    continue;
                }
                const port = new NodesPort(name, portData.groups, portData.x, portData.z);
                Nodes.ports.set(name, port);
            }

            // create jsx
            Nodes._updateAllPortJsx();

            // re-render world
            if ( render ) {
                Nodes.renderWorld();
            }
        }
        else {
            console.error("Invalid load metadata")
        }
    },

    // serialize world into a json object
    save: () => {
        // pre-calculations for on-save data
        Nodes.wasmWorld.calculateNeighbors();
        
        // generate territory colors
        Nodes.wasmWorld.generateColors();

        // serialize resource nodes
        let nodes = {};
        Nodes.nodes.forEach((node, name) => {
            nodes[name] = node;
        });
        
        // serialize territories
        let territories = {};
        Nodes.territories.forEach((terr, id) => {
            if ( terr.size > 0 ) {
                territories[id] = {
                    name: terr.name,
                    core: terr.core !== undefined ? [terr.core.x, terr.core.y] : null,
                    coreChunk: terr.coreChunk !== undefined ? [terr.coreChunk.x, terr.coreChunk.y] : null,
                    chunks: Array.from(Nodes.wasmWorld.getTerritoryChunksBuffer(id)),
                    size: terr.size,
                    neighbors: Array.from(Nodes.wasmWorld.getTerritoryNeighbors(id)),
                    isEdge: Nodes.wasmWorld.getTerritoryIsEdge(id),
                    nodes: terr.nodes,
                    color: Nodes.wasmWorld.getTerritoryColor(terr.id),
                };
            }
        });

        // world object
        let world = {
            meta: {
                type: "world"
            },
            nodes: nodes,
            territories: territories,
        };
        let blob = new Blob([JSON.stringify(world)], {type: "application/json"});
        saveAs(blob, "world.json");
    },

    /**
     * Serialize and save towns data into a json file.
     */
    saveTowns: () => {
        // serialize residents
        let residents = {};
        Nodes.residents.forEach((r, name) => {
            residents[name] = r;
        });

        // serialize towns
        let towns = {};
        Nodes.towns.forEach((t, name) => {
            towns[name] = t.export();
        });

        // serialize nations
        let nations = {};
        Nodes.nations.forEach((n, name) => {
            nations[name] = n.export();
        });

        let townsJson = {
            meta: {
                type: "towns"
            },
            residents: residents,
            towns: towns,
            nations: nations,
        };

        console.log(townsJson);

        let blob = new Blob([JSON.stringify(townsJson, null, 2)], {type: "application/json"});
        saveAs(blob, "towns.json");
    },

    // ============================================
    // Generic set editor option
    // ============================================

    /**
     * Handle setting a generic property in the Nodes global
     * state storage, optionally do re-renders of UI.
     * 
     * setting - string key of option, Nodes[setting]
     * val - new value
     * doUpdateTerritories - run `Nodes._updateAllTerritoryElements`
     * doRenderWorld - run `Nodes.renderWorld`
     * doRenderEditor - run `Nodes.renderEditor`
     */
    setSetting: (
        setting,
        val,
        doUpdateTerritories = false,
        doRenderWorld = false,
        doRenderEditor = false,
    ) => {
        if ( Nodes.hasOwnProperty(setting) ) {
            Nodes[setting] = val;

            if ( doUpdateTerritories ) {
                Nodes._updateAllTerritoryElements();
            }
            if ( doRenderWorld ) {
                Nodes.renderWorld();
            }
            if ( doRenderEditor ) {
                Nodes.renderEditor();
            }
        }
        else {
            console.error(`No "${setting}" property in Nodes`);
        }
    },

    // ============================================
    // Set render options
    // TODO: collapse these into a single function?
    // ============================================
    
    setRenderTerritoryIcons: (val) => {
        Nodes.renderTerritoryIcons = val;
        Nodes._updateAllTerritoryElements();
        Nodes.renderWorld();
        Nodes.renderEditor();
    },

    setRenderTerritoryId: (val) => {
        Nodes.renderTerritoryId = val;
        Nodes._updateAllTerritoryElements();
        Nodes.renderWorld();
        Nodes.renderEditor();
    },

    setRenderTerritoryCost: (val) => {
        Nodes.renderTerritoryCost = val;
        Nodes._updateAllTerritoryElements();
        Nodes.renderWorld();
        Nodes.renderEditor();
    },

    setRenderTerritoryOpaque: (val) => {
        Nodes.renderTerritoryOpaque = val;
        Nodes._updateAllTerritoryElements();
        Nodes.renderWorld();
        Nodes.renderEditor();
    },

    setRenderTerritoryNoBorders: (val) => {
        Nodes.renderTerritoryNoBorders = val;
        Nodes._updateAllTerritoryElements();
        Nodes.renderWorld();
        Nodes.renderEditor();
    },

    setRenderTerritoryCapitals: (val) => {
        Nodes.renderTerritoryCapitals = val;
        Nodes._updateAllTownCapitalsJsx();
        Nodes._updateAllTownNameTagJsx(); // update this because nametag offsets if capital icons are rendered
        Nodes.renderWorld();
        Nodes.renderEditor();
    },

    setRenderTownNames: (val) => {
        Nodes.renderTownNames = val;
        Nodes._updateAllTownNameTagJsx();
        Nodes.renderWorld();
        Nodes.renderEditor();
    },


    // ============================================
    // Render React functions
    // ============================================

    /**
     * Render territorys onto dynmap
     */
    renderWorld: () => {
        const props = {
            territories: Nodes.territories,
            renderTerritoryColors: Nodes.renderTerritoryColors,
            width: Nodes.mapWidth,
            height: Nodes.mapHeight,
            viewbox: Nodes.mapViewbox,
            transform: Nodes.mapTransform,
            dynmap: Nodes.dynmap,
            cursorX: Nodes.cursorX,
            cursorY: Nodes.cursorY,
            cursorLatLng: Nodes.cursorLatLng,
            selectedTerritory: Nodes.selectedTerritory,
            selectTerritory: Nodes._selectTerritory,
            getNodeIcon: Nodes._getNodeIcon,
            svgPatterns: Nodes.stripePatterns,
            territoryElements: Nodes.territoryElements,
            portElements: Nodes.portsJsx,
            townCapitalElements: Nodes.townCapitalElementsJsx,
            townNameElements: Nodes.townNameElementsJsx,
            enabledPainting: Nodes.enabledPainting,
            isErasing: Nodes.ctrlKey,
            paintRadius: Nodes.paintRadius,
            startPaint: Nodes._startPaint,
            endPaint: Nodes._endPaint,
            handleMouseDown: handleMouseDown,
            handleMouseUp: handleMouseUp,
            handleWindowMouseUp: handleWindowMouseUp,
            backgroundImageSrc: Nodes.backgroundImageSrc,
            backgroundImageOriginX: Nodes.backgroundImageOriginX,
            backgroundImageOriginY: Nodes.backgroundImageOriginY,
            backgroundImageEndX: Nodes.backgroundImageEndX,
            backgroundImageEndY: Nodes.backgroundImageEndY,
        };

        Nodes.dynmapSvgContainerRoot?.render(<WorldRenderer {...props}/>);
    },

    /**
     * Renders sidepanel editor
     */
    renderEditor: () => {
        const props = {
            uploadEnabled: uploadEnabled,
            editorEnabled: editorEnabled,

            save: Nodes.save,
            saveTowns: Nodes.saveTowns,
            load: Nodes.load,
            x: Nodes.chunkX,
            z: Nodes.chunkY,

            // world options
            renderTerritoryIcons: Nodes.renderTerritoryIcons,
            setRenderTerritoryIcons: Nodes.setRenderTerritoryIcons,
            renderTerritoryId: Nodes.renderTerritoryId,
            setRenderTerritoryId: Nodes.setRenderTerritoryId,
            renderTerritoryCost: Nodes.renderTerritoryCost,
            setRenderTerritoryCost: Nodes.setRenderTerritoryCost,
            renderTerritoryOpaque: Nodes.renderTerritoryOpaque,
            setRenderTerritoryOpaque: Nodes.setRenderTerritoryOpaque,
            renderTerritoryNoBorders: Nodes.renderTerritoryNoBorders,
            setRenderTerritoryNoBorders: Nodes.setRenderTerritoryNoBorders,
            renderTerritoryCapitals: Nodes.renderTerritoryCapitals,
            setRenderTerritoryCapitals: Nodes.setRenderTerritoryCapitals,
            renderTownNames: Nodes.renderTownNames,
            setRenderTownNames: Nodes.setRenderTownNames,

            // nodes data
            nodes: Nodes.nodes,
            nodesNameList: Nodes.nodesNameList,
            createNode: Nodes._createNode,
            deleteNode: Nodes._deleteNode,
            selectedNodeIndex: Nodes.selectedNodeIndex,
            setSelectedNode: Nodes._setSelectedNode,
            renameNode: Nodes._renameNode,
            setNodeIcon: Nodes._setNodeIcon,
            setNodeData: Nodes._setNodeData,
            
            // global data
            territoryCost: Nodes.territoryCost,
            setTerritoryCost: Nodes._setTerritoryCost,
            resourceIcons: Nodes.resourceIcons,

            // territory data
            selectedTerritory: Nodes.selectedTerritory,
            createTerritory: Nodes._createTerritory,
            deleteTerritory: Nodes._deleteTerritory,
            setTerritoryName: Nodes._setTerritoryName,
            addNodeToTerritory: Nodes._addNodeToTerritory,
            removeNodeFromTerritory: Nodes._removeNodeFromTerritory,
            setPainting: Nodes._setPainting,
            togglePainting: Nodes._togglePainting,
            paintRadius: Nodes.paintRadius,
            setEditingTerritoryNodes: Nodes._setEditingTerritoryNodes,

            // towns data
            towns: Nodes.townsList,
            selectedTown: Nodes.selectedTown,
            selectedTownIndex: Nodes.selectedTownIndex,
            selectTown: Nodes._selectTown,
            setTownSortKey: Nodes.setTownSortKey,
            createTown: Nodes.createTown,
            deleteTown: Nodes.deleteTown,
            setTownName: Nodes.setTownName,
            setTownNationFromName: Nodes.setTownNationFromName,
            setTownHome: Nodes.setTownHome,
            addTownResident: Nodes.addTownResident,
            removeTownResident: Nodes.removeTownResident,
            setSelectedTownHomeToSelectedTerritory: Nodes.setSelectedTownHomeToSelectedTerritory,
            addSelectedTownSelectedTerritories: Nodes.addSelectedTownSelectedTerritories,
            addSelectedTownSelectedTerritoriesAsCaptured: Nodes.addSelectedTownSelectedTerritoriesAsCaptured,
            removeSelectedTownSelectedTerritories: Nodes.removeSelectedTownSelectedTerritories,
            removeSelectedTerritoriesOwned: Nodes.removeSelectedTerritoriesOwned,
            removeSelectedTerritoriesCaptured: Nodes.removeSelectedTerritoriesCaptured,
            setSelectedTownColor: Nodes.setSelectedTownColor,
            setSelectedTownNationColor: Nodes.setSelectedTownNationColor,
        };

        Nodes.editorContainerRoot?.render(<Editor {...props}/>);
    },

    // handler for mouse movement
    handleMouseMove: (event) => {
        if ( !Nodes.dynmap.map ) {
            return;
        }

        // update cursor
        const loc = Nodes.dynmap.getProjection().fromLatLngToLocation(event.latlng, Nodes.dynmap.world.sealevel+1);
        Nodes.cursorX = loc.x;
        Nodes.cursorY = loc.y;

        Nodes.chunkX = Math.floor(loc.x/16);
        Nodes.chunkY = Math.floor(loc.z/16);

        if ( Nodes.enabledPainting ) {
            // shift key -> change brush size
            if ( Nodes.shiftKey ) {
                const sx = event.originalEvent.screenX;
                const sy = event.originalEvent.screenY;
                const dx = sx - Nodes.screenX;
                const dy = -(sy - Nodes.screenY); // invert because I want move up as positive
                Nodes.screenX = sx;
                Nodes.screenY = sy;

                // for increase vs. decrease, choose absolute value max
                const direction = ( Math.abs(dx) > Math.abs(dy) ) ? dx : dy;

                // multiplier for radius change
                const changeScale = 0.1; 

                // set new radius
                const newRadius = Math.max(Nodes.minPaintRadius, Math.min(Nodes.maxPaintRadius, Nodes.paintRadius + changeScale * direction));
                Nodes.paintRadius = newRadius;

                // exit before updating cursor lat/long used for paint brush location
                Nodes.renderEditor();
                Nodes.renderWorld();
                return
            }
            // if key down, paint
            else if ( Nodes.isPainting ) {
                Nodes._paint();
            }

        }

        Nodes.screenX = event.originalEvent.screenX;
        Nodes.screenY = event.originalEvent.screenY;
        Nodes.cursorLatLng = event.latlng;

        Nodes.renderEditor();
        Nodes.renderWorld();
    },

    /**
     * Set dynmap initial map transform:
     * width: svg width
     * height: svg height
     * viewbox: [min.x, min.y, width, height]
     * transform: translate3d string
     * zoom: zoom
     */
    setInitialMapTransform: (width, height, viewbox, transform, zoom) => {
        Nodes.mapWidth = width;
        Nodes.mapHeight = height;
        Nodes.mapViewbox = viewbox;
        Nodes.mapTransform = transform;
        Nodes.mapZoom = zoom;
    },

    /**
     * Inputs:
     * width: svg width
     * height: svg height
     * viewbox: [min.x, min.y, width, height]
     * transform: translate3d string
     */
    setMapTransform: (width, height, viewbox, transform, zoom) => {
        Nodes.mapWidth = width;
        Nodes.mapHeight = height;
        Nodes.mapViewbox = viewbox;
        Nodes.mapTransform = transform;

        // update render
        if ( Nodes.mapZoom !== zoom ) {
            Nodes.mapZoom = zoom;
            Nodes._updateAllTerritoryElements();
            Nodes._updateAllTownCapitalsJsx();
            Nodes._updateAllTownNameTagJsx();
            Nodes._updateAllPortJsx();
        }

        Nodes.renderWorld();
    },

    // =====================================
    // internal
    // =====================================

    /**
     * Clear all world territory and resource state.
     * Does not clear towns or nations (which are separate "layer" that
     * only references territories/resources state).
     */
    _clearWorld: () => {
        Nodes.nodes.clear();
        Nodes.nodesNameList = [];
        Nodes.territories.clear();
        Nodes.territoryIdToIndex.clear();
        Nodes.wasmWorld.clear();
        Nodes.wasmWorld.setTerritoryIdCounter(0);
    },

    /**
     * Clear towns and nations.
     */
    _clearTowns: () => {
        Nodes.residents.clear();
        Nodes.towns.clear();
        Nodes.townsNameList = [];
        Nodes.nations.clear();

        // remove links in territories
        Nodes.territories.forEach((territory, id) => {
            territory.town = undefined;
            territory.occupier = undefined;
        });
    },

    /**
     * Clear ports.
     */
    _clearPorts: () => {
        Nodes.ports.clear();
        Nodes.portsJsx = [];
    },

    /**
     * Clear all resource definitions and strip all resources from
     * territories.
     */
    _clearResources: () => {
        Nodes.nodes = new Map();
        Nodes.nodesNameList = [];
        
        Nodes.territories.forEach((territory, id) => {
            territory.nodes = [];
        });
    },

    // =====================================
    // town/nation stuff
    // =====================================
    
    /**
     * Create a new blank town with input name. If `newName` is undefined,
     * this will try to name the town as "townN", incrementing integer N
     * until a  free town name is found.
     * 
     * If `selectNewTown` is true, this will make the newly created town
     * selected.
     */
    createTown: (name = undefined, selectNewTown = true) => {
        let newTownName = name;
        if ( newTownName === undefined || newTownName === null ) {
            let n = 0;
            do {
                newTownName = `town${n}`;
                n += 1;
            } while ( Nodes.towns.has(newTownName) )
        }
        else {
            // new name was inputed, if it already exists, error and stop
            if ( Node.towns.has(newTownName) ) {
                console.error(`Cannot create new town ${newTownName} because name already exists.`);
                return;
            }
        }

        const town = new NodesTown({
            name: newTownName,
        });
        
        // set town in storage and update towns list
        Nodes.towns.set(newTownName, town);
        Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);

        if ( selectNewTown ) {
            Nodes.selectedTown = town;
            Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid === town.uuid);
        }

        // re-render
        Nodes.renderEditor()
    },

    /**
     * Delete town from its name, if it exists.
     */
    deleteTown: (name) => {
        let town = Nodes.towns.get(name);

        if ( town === undefined ) {
            console.log(`Town with name ${name} does not exist, cannot be deleted.`);
            return;
        }
        
        // remove town from storage and update towns list
        Nodes.towns.delete(name);
        Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);

        // if deleted town was the selected town, de-select
        if ( town == Nodes.selectedTown ) {
            Nodes.selectedTown = undefined;
            Nodes.selectedTownIndex = undefined; 
        } else { // need to re-find selected town's index in updated list
            if ( Nodes.selectedTown !== undefined ) {
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid === Nodes.selectedTown.uuid);
            }
        }
        
        // update town's territories
        const modifiedTerritoryIds = [];
        town.territories.forEach(terrId => {
            const terr = Nodes.territories.get(terrId);
            if ( terr !== undefined ) {
                terr.town = undefined;
            }
            modifiedTerritoryIds.push(terrId);
        });
        town.captured.forEach(terrId => {
            const terr = Nodes.territories.get(terrId);
            if ( terr !== undefined ) {
                terr.occupier = undefined;
            }
            modifiedTerritoryIds.push(terrId);
        });
        Nodes._updateTerritoryElementIds(modifiedTerritoryIds);

        // re-render
        Nodes.renderEditor();
        Nodes.renderWorld();
    },

    /**
     * Set a town's name.
     */
    setTownName: (town, newTownName) => {
        console.log(`Setting town name ${town} ${town.name} to ${newTownName}`);

        if ( town === undefined || town === null ) {
            return;
        }

        // if new town name same, ignore
        if ( town.name === newTownName ) {
            return;
        }

        // if town with name already exists, print error and return
        if ( Nodes.towns.has(newTownName) ) {
            console.error(`Cannot rename ${town.name} => ${newTownName} because name already exists.`);
            return;
        }

        // remove old town in towns map
        if ( Nodes.towns.delete(town.name) !== true ) {
            console.error (`Failed to delete old town named ${town.name} from towns map`);
            return;
        }

        town.name = newTownName;
        Nodes.towns.set(newTownName, town);

        // update towns list
        Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);

        // if this was selected town, re-find selected index
        if ( town == Nodes.selectedTown ) {
            Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.name == newTownName);
        }

        // re-render
        Nodes.renderEditor()
    },

    /**
     * Set a town's home territory id.
     */
    setTownHome: (town, newHomeId) => {
        if ( town === undefined || town === null ) {
            return;
        }

        const modifiedTerritoryIds = [];

        // parse to make sure newHomeId is an integer
        const newHomeIdInt = parseInt(newHomeId);
        
        // make sure new home is within town's territories
        if ( !town.territories.includes(newHomeIdInt) ) {
            console.error(`Cannot set town ${town.name} home to ${newHomeIdInt} because it is not a territory in the town.`);
            return;
        }

        // update town home. if either home id is >0, need
        // to re-render that territory on the map
        if ( town.home > 0 ) {
            modifiedTerritoryIds.push(town.home);
        }
        
        town.home = newHomeIdInt;
        
        if ( newHomeIdInt > 0 ) {
            modifiedTerritoryIds.push(newHomeIdInt);
        }

        Nodes._updateTerritoryElementIds(modifiedTerritoryIds);
        Nodes.renderWorld();
    },

    /**
     * Wrapper to set selected town's home to first selected territory.
     */
    setSelectedTownHomeToSelectedTerritory: () => {
        if ( Nodes.selectedTown === undefined || Nodes.selectedTown === null ) {
            console.error(`setSelectedTownHomeToSelectedTerritory: No selected town.`)
            return;
        }
        if ( Nodes.selectedTerritory === undefined || Nodes.selectedTerritory === null ) {
            console.error(`setSelectedTownHomeToSelectedTerritory: No selected territory.`)
            return;
        }

        Nodes.setTownHome(Nodes.selectedTown, Nodes.selectedTerritory.id);
    },

    /**
     * Set a town's nation's from name. For now, this has multiple effects
     * depending on current and new nation name 
     * (TODO: this has too many different internal functionalities, need to
     * split up in future).
     * - Old nation name == "" and new name == "":
     *      Remove town from its nation.
     * - Old nation name != "" and new name != "":
     *      Change nation name. This updates nation, but does not move
     *      any towns in or out of this nation.
     * - Old nation name == "" and new name != "":
     *      Add town to new nation. If nation does not exist, create it.
     */
    setTownNationFromName: (town, newNationName) => {
        if ( town === undefined || town === null ) {
            console.error("setTownNationFromName: Town undefined, cannot set nation");
            return;
        }

        // if new nation name same, ignore
        if ( town.nation === newNationName ) {
            return;
        }

        // current town is in a nation: either remove, or modify nation name
        if ( town.nation !== undefined ) {
            // if new nation name is empty string, remove town from nation
            if ( newNationName === "" ) {
                const nation = Nodes.nations.get(town.nation);
                if ( nation !== undefined ) {
                    nation.capital = nation.capital === town.name ? undefined : nation.capital;
                    nation.towns = nation.towns.filter(t => t !== town.name);
                }
                town.nation = undefined;
                town.color = town.colorTown;
                Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid === town.uuid);
                Nodes._updateStripePatterns();
                Nodes._updateTerritoryElementIds([...town.territories, ...town.captured]);
            }
            // switch to different nation if it does exist,
            // or change nation name if new name does not exist,
            else {
                const oldNationName = town.nation;
                let oldNation = Nodes.nations.get(oldNationName);
                let newNation = Nodes.nations.get(newNationName);
                // switch town into new nation
                if ( newNation !== undefined ) {
                    if ( oldNation !== undefined ) {
                        oldNation.capital = oldNation.capital === town.name ? undefined : oldNation.capital;
                        oldNation.towns = oldNation.towns.filter(t => t !== town.name);
                    }
                    newNation.towns = [...newNation.towns, town.name];

                    town.nation = newNationName;
                    town.colorNation = [...newNation.color];
                    town.color = town.colorNation;
                }
                // change nation name
                else {
                    if ( oldNation !== undefined ) {
                        oldNation.towns.forEach(tName => {
                            const t = Nodes.towns.get(tName);
                            if ( t !== undefined ) {
                                t.nation = newNationName;
                            }
                        });
                        Nodes.nations.delete(oldNationName);
                        Nodes.nations.set(newNationName, oldNation);
                    }
                }

                Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid === town.uuid);
                Nodes._updateStripePatterns();
                Nodes._updateTerritoryElementIds([...town.territories, ...town.captured]);
            }

            Nodes.renderEditor();
            Nodes.renderWorld();
        }
        else {
            if ( newNationName === "" ) {
                return; // do nothing
            }
            // add town to new nation, if it does not exist create new nation
            else {
                let newNation = Nodes.nations.get(newNationName);
                if ( newNation === undefined ) {
                    newNation = new NodesNation({});
                }
                newNation.towns = [...newNation.towns, town.name];
                Nodes.nations.set(newNationName, newNation);

                town.nation = newNationName;
                town.colorNation = [...newNation.color];
                town.color = town.colorNation;

                Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid === town.uuid);
                Nodes._updateStripePatterns();
                Nodes._updateTerritoryElementIds([...town.territories, ...town.captured]);

                Nodes.renderEditor();
                Nodes.renderWorld();
            }
        }
    },

    /**
     * Add resident to a town. If town does not exist this will early exit
     * printing an error and do nothing. If player does not exist in
     * residents storage, player will be added as an "Anonymous" player
     * with a new random uuid. Optionally specify new resident's rank
     * (default, officer, or leader).
     */
    addTownResident: (town, playerName, rank = RESIDENT_RANK_NONE) => {
        // if town undefined early exit
        if ( town === undefined || town === null ) {
            console.log("Town undefined, cannot add resident");
            return;
        }
        
        // if player already in town, skip
        if ( town.residents.some(r => r.name === playerName) ) {
            console.error(`Town ${town.name} already has resident ${playerName}, skipping`);
            return;
        }

        // append new resident and re-create town residents array
        // (to trigger useMemo re-render)
        // if player exists in residents, use existing player's uuid
        // otherwise, generate a new random uuid for player.
        // NOTE: this uuid will not match the real mineman uuid...
        // todo: resolve by mineman auth lookup? https://wiki.vg/Mojang_API
        let playerUuid = Nodes.residents.get(playerName)?.uuid ?? uuidv4();

        town.residents = [...town.residents, new NodesResident({
            uuid: playerUuid,
            name: playerName,
            rank: rank,
        })];

        town.residents = Nodes._sortTownResidents(town);
        town.residentUuids = town.residents.map(r => r.uuid);

        // if sort order is by player count, re-sort towns list
        if ( Nodes.townsSortKey === TownSortKey.PLAYERS ) {
            Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
            // if this was selected town, re-find selected index
            if ( town == Nodes.selectedTown ) {
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid == town.uuid);
            }
        }
    },

    /**
     * Remove resident player from a town. If town or player do not exist,
     * this will early printing an error and doing nothing. 
     */
    removeTownResident: (town, playerName) => {
        // if town undefined early exit
        if ( town === undefined || town === null ) {
            console.error("Town undefined, cannot add resident");
            return;
        }
        
        // find player index in residents array
        const playerIndex = town.residents.findIndex(r => r.name === playerName);
        if ( playerIndex === -1 ) {
            console.error(`Town ${town.name} does not have resident ${playerName}, skipping`);
            return;
        }

        // if player is leader or officer, update those fields as well
        const resident = town.residents[playerIndex];
        if ( town.leader === resident.uuid ) {
            town.leader = undefined;
        }
        town.officers = town.officers?.filter(uuid => uuid !== resident.uuid) ?? [];
        town.residents = [
            ...town.residents.slice(0, playerIndex),
            ...town.residents.slice(playerIndex + 1),
        ];
        town.residents = Nodes._sortTownResidents(town);

        // if sort order is by player count, re-sort towns list
        if ( Nodes.townsSortKey === TownSortKey.PLAYERS ) {
            Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
            // if this was selected town, re-find selected index
            if ( town == Nodes.selectedTown ) {
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid == town.uuid);
            }
        }

        // force re-render to update players list
        Nodes.renderEditor();
    },

    /**
     * Add array of territory ids to a town. If town does not exist, print
     * an error and early exist. Skip any of the territories that do not
     * exist or are are owned by another town.
     */
    addTownTerritories: (town, territories) => {
        if ( town === undefined || town === null ) {
            console.error("Town undefined, cannot add territories");
            return;
        }

        if ( territories === undefined || territories === null || territories.size === 0 ) {
            console.error("No territories to add");
            return;
        }

        // for each territory, add link to town
        territories.forEach((terr, _terrId) => {
            // remove from previous town first
            if ( terr.town !== undefined ) {
                terr.town.territories = terr.town.territories.filter(t => t !== terr.uuid);
            }
            terr.town = town;
        });

        // add territories to town: use a set to remove duplicates
        const territoryIds = Array.from(territories.keys());
        town.territories = [...new Set([...town.territories, ...territoryIds])];

        // force re-render modified territories in world map
        Nodes._updateTerritoryElementIds(territoryIds);

        // if sort order is by territory count, re-sort towns list
        if ( Nodes.townsSortKey === TownSortKey.TERRITORIES ) {
            Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
            // if this was selected town, re-find selected index
            if ( town == Nodes.selectedTown ) {
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid == town.uuid);
            }
        }

        Nodes.renderWorld();
    },

    /**
     * Add array of territory ids to a town. If town does not exist, print
     * an error and early exist. Skip any of the territories that do not
     * exist or are are owned by another town.
     */
    addTownCapturedTerritories: (town, territories) => {
        if ( town === undefined || town === null ) {
            console.error("Town undefined, cannot add territories");
            return;
        }

        if ( territories === undefined || territories === null || territories.size === 0 ) {
            console.error("No territories to add");
            return;
        }

        // for each territory, link occupier to input town
        // if town not undefined and if town is not same as input town
        const validTerritoryIds = [];
        territories.forEach((terr, _terrId) => {
            if ( terr.town !== undefined && terr.town !== null && terr.town.uuid !== town.uuid ) {
                terr.occupier = town;
                validTerritoryIds.push(terr.id);
            }
        });

        // add territories to town's captured territories (use set to dedup)
        town.captured = [...new Set([...town.captured, ...validTerritoryIds])];

        // update stripe patterns
        Nodes._updateStripePatterns();

        // force re-render modified territories in world map
        Nodes._updateTerritoryElementIds(validTerritoryIds);
        Nodes.renderWorld();
    },
    
    /**
     * Remove an array of territory ids owned by a town. If town does not
     * exist, print an error and early exit. Skip any of the territories
     * that either do not exist or are not owned by the town. 
     */
    removeTownTerritories: (town, territories) => {
        if ( town === undefined || town === null ) {
            console.error("Town undefined, cannot add territories");
            return;
        }

        if ( territories === undefined || territories === null || territories.size === 0 ) {
            console.error("No territories to remove");
            return;
        }

        // for each territory, remove link to town owner or occupier
        territories.forEach((terr, _terrId) => {
            if ( terr.town?.uuid === town.uuid ) {
                terr.town = undefined;
            }
            if ( terr.occupier?.uuid === town.uuid ) {
                terr.occupier = undefined;
            }
        });

        // remove territories from town's owned, "annexed", and "captured"
        // territories.
        town.territories = town.territories.filter(t => !territories.has(t));
        town.annexed = town.annexed.filter(t => !territories.has(t));
        town.captured = town.captured.filter(t => !territories.has(t));

        // if territory removed was town's home, remove home id
        if ( territories.has(town.home) ) {
            town.home = -1;
        }
        
        // force re-render modified territories in world map
        const territoryIds = Array.from(territories.keys());
        Nodes._updateTerritoryElementIds(territoryIds);

        // if sort order is by territory count, re-sort towns list
        if ( Nodes.townsSortKey === TownSortKey.TERRITORIES ) {
            Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
            // if this was selected town, re-find selected index
            if ( town == Nodes.selectedTown ) {
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid == town.uuid);
            }
        }

        Nodes.renderWorld();
    },

    /**
     * Wrapper to add currently selected territories to selected town.
     * If selected town is undefined or selected territories empty,
     * print an error and early exit.
     */
    addSelectedTownSelectedTerritories: () => {
        Nodes.addTownTerritories(Nodes.selectedTown, Nodes.selectedTerritories);
    },

    /**
     * Wrapper to add currently selected territories to selected town
     * as "captured" territories (occupied territory owned by another town).
     * If selected town is undefined or selected territories empty,
     * print an error and early exit.
     */
    addSelectedTownSelectedTerritoriesAsCaptured: () => {
        Nodes.addTownCapturedTerritories(Nodes.selectedTown, Nodes.selectedTerritories);
    },
    
    /**
     * Wrapper to remove currently selected territories from selected town.
     * If selected town is undefined or selected territories empty,
     * print an error and early exit.
    */
    removeSelectedTownSelectedTerritories: () => {
        Nodes.removeTownTerritories(Nodes.selectedTown, Nodes.selectedTerritories);
    },

    /**
     * Update editor towns list sort key and re-render towns list.
     */
    setTownSortKey: (sortKey) => {
        Nodes.townsSortKey = sortKey;
        Nodes.townsList = Nodes._sortTownsNations(sortKey);
        // update selected town index
        if ( Nodes.selectedTown !== undefined ) {
            Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid === Nodes.selectedTown.uuid);
        }
        Nodes.renderEditor();
    },

    /**
     * Remove an array of territory ids from all their town owners.
     */
    removeTerritoriesOwned: (territories) => {
        if ( territories === undefined || territories === null || territories.size === 0 ) {
            console.error("No territories to remove");
            return;
        }

        // for each territory, remove link to town owner or occupier
        // mark town or occupier as modified, will update later
        const modifiedTownIds = new Set();

        territories.forEach((terr, _terrId) => {
            if ( terr.town !== undefined ) {
                modifiedTownIds.add(terr.town.uuid);
                terr.town = undefined;
            }
            if ( terr.occupier !== undefined ) {
                modifiedTownIds.add(terr.occupier.uuid);
                terr.occupier = undefined;
            }
        });

        // remove territories from town's owned, "annexed", and "captured"
        modifiedTownIds.forEach(townId => {
            const town = Nodes.towns.get(townId);
            if ( town !== undefined ) {
                town.territories = town.territories.filter(t => !territories.has(t));
                town.annexed = town.annexed.filter(t => !territories.has(t));
                town.captured = town.captured.filter(t => !territories.has(t));
                
                // if territory removed was town's home, remove home id
                if ( territories.has(town.home) ) {
                    town.home = -1;
                }
            }
        });
        
        // force re-render modified territories in world map
        const territoryIds = Array.from(territories.keys());
        Nodes._updateStripePatterns();
        Nodes._updateTerritoryElementIds(territoryIds);

        // if sort order is by territory count, re-sort towns list
        if ( Nodes.townsSortKey === TownSortKey.TERRITORIES ) {
            Nodes.townsList = Nodes._sortTownsNations(Nodes.townsSortKey);
            // if this was selected town, re-find selected index
            if ( Nodes.selectedTown !== undefined ) {
                Nodes.selectedTownIndex = Nodes.townsList.findIndex(t => t.uuid == Nodes.selectedTown.uuid);
            }
        }

        Nodes.renderEditor();
        Nodes.renderWorld();
    },

    /**
     * Remove an array of territory ids from their occupier town.
     * This only releases "captured" territories, which are occupied
     * by a town different than its actual towner.
     */
    removeTerritoriesCaptured: (territories) => {
        if ( territories === undefined || territories === null || territories.size === 0 ) {
            console.error("No territories to remove");
            return;
        }

        // for each territory, remove link to town owner or occupier
        // mark town or occupier as modified, will update later
        const modifiedTownIds = new Set();

        territories.forEach((terr, _terrId) => {
            if ( terr.occupier !== undefined ) {
                modifiedTownIds.add(terr.occupier.uuid);
                terr.occupier = undefined;
            }
        });

        // remove territories from town's owned, "annexed", and "captured"
        modifiedTownIds.forEach(townId => {
            const town = Nodes.towns.get(townId);
            if ( town !== undefined ) {
                town.captured = town.captured.filter(t => !territories.has(t));
            }
        });
        
        // force re-render modified territories in world map
        const territoryIds = Array.from(territories.keys());
        Nodes._updateStripePatterns();
        Nodes._updateTerritoryElementIds(territoryIds);
        Nodes.renderWorld();
    },

    /**
     * Wrapper to remove currently selected territories from their
     * town owners and occupiers.
     */
    removeSelectedTerritoriesOwned: () => {
        Nodes.removeTerritoriesOwned(Nodes.selectedTerritories);
    },

    /**
     * Wrapper to remove currently selected territories from thier
     * occupier only (not actual town owner).
     */
    removeSelectedTerritoriesCaptured: () => {
        Nodes.removeTerritoriesCaptured(Nodes.selectedTerritories);
    },

    /**
     * Set a town's color. Color input is array in format [r, g, b].
     * Update town color and re-render all of town's territory colors.
     * If town input is undefined, print error and do nothing.
     */
    setTownColor: (town, color) => {
        if ( town === undefined || town === null ) {
            console.error("setTownColor: town is undefined");
            return;
        }

        if ( color === undefined || color === null ) {
            console.error("setTownColor: color is undefined");
            return;
        }

        // town's color
        town.colorTown = color;

        // only need to re-render if actually using town's color.
        // if town is part of a nation, nation color is used instead,
        // so no re-render needed
        if ( town.nation === undefined ) {
            town.color = color;

            Nodes._updateStripePatterns();
            Nodes._updateTerritoryElementIds([].concat(town.territories, town.captured));
    
            Nodes.renderEditor();
            Nodes.renderWorld();
        }
    },

    /**
     * Wrapper to set the currently selected town's color. Color input
     * is array in format [r, g, b]. If no town selected, print error
     * and do nothing.
     */
    setSelectedTownColor: (color) => {
        if ( Nodes.selectedTown === undefined || Nodes.selectedTown === null ) {
            console.error("setSelectedTownColor: no town selected");
            return;
        }

        Nodes.setTownColor(Nodes.selectedTown, color);
    },

    /**
     * Set a nation's color. Color input is array in format [r, g, b].
     * Must update a nation and update all colors for town's within nation
     * (which share the same color on the territory map).
     * If nation or color undefined, print error and do nothing.
     */
    setNationColor: (nation, color) => {
        if ( nation === undefined || nation === null ) {
            console.error("setNationColor: nation is undefined");
            return;
        }

        if ( color === undefined || color === null ) {
            console.error("setNationColor: color is undefined");
            return;
        }

        nation.color = color;

        // town territories that need to be updated
        const territoriesChangedIds = [];

        nation.towns
            .map(townId => Nodes.towns.get(townId))
            .forEach(town => {
                if ( town !== undefined ) {
                    town.colorNation = color;
                    town.color = color;
                    territoriesChangedIds.push(...town.territories, ...town.captured);
                }
            });
        
        Nodes._updateStripePatterns();
        Nodes._updateTerritoryElementIds(territoriesChangedIds);

        Nodes.renderEditor();
        Nodes.renderWorld();
    },

    /**
     * Wrapper to set the currently selected town's nation color. Color
     * input is array in format [r, g, b]. If no town selected, print
     * error and do nothing.
     */
    setSelectedTownNationColor: (color) => {
        if ( Nodes.selectedTown === undefined ) {
            console.error("setSelectedTownColor: no town selected");
            return;
        }

        const nation = Nodes.nations.get(Nodes.selectedTown.nation);
        if ( nation === undefined ) {
            console.error("setSelectedTownColor: town has no nation");
            return;
        }

        Nodes.setNationColor(nation, color);
    },

    /**
     * Sort input town data:
     * 1. sort nations by player count (high-to-low)
     * 2. sort towns in each nation by player count (high-to-low)
     * 
     * Nation-less towns inserted at end, also sorted by player count.
     */
    _sortTownsNations: (sortKey = TownSortKey.TERRITORIES) => {
        let townsList = [];
        
        // store array of:
        // {
        //    nation: nation,
        //    towns: [town1, town2, ...],
        // }
        let nationsAndTowns = [];

        // first count # of players and # of territories in each nation
        // mutate by adding property to nation
        // TODO: should do this elsewhere i guess...
        Nodes.nations.forEach((nation, nationName) => {
            let numPlayers = 0;
            let numTerritories = 0;
            let nationTowns = [];
            for ( const townName of nation.towns ) {
                const town = Nodes.towns.get(townName);
                if ( town !== undefined ) {
                    numPlayers += town.residents.length;
                    numTerritories += town.territories.length;
                    nationTowns.push(town);
                }
            }
            nation.numPlayers = numPlayers;
            nation.numTerritories = numTerritories;

            nationsAndTowns.push({
                nationName: nationName,
                nation: nation,
                towns: nationTowns,
            });
        });

        // nation sorting functions, map from enum => function
        const nationSortFunctions = {
            [TownSortKey.ALPHABETICAL]: (a, b) => a.nationName.localeCompare(b.nationName),
            [TownSortKey.PLAYERS]: (a, b) => b.nation.numPlayers - a.nation.numPlayers,
            [TownSortKey.TERRITORIES]: (a, b) => b.nation.numTerritories - a.nation.numTerritories,
        };

        // town sorting functions
        const townSortFunctions = {
            [TownSortKey.ALPHABETICAL]: (a, b) => a.name.localeCompare(b.name),
            [TownSortKey.PLAYERS]: (a, b) => b.residents.length - a.residents.length,
            [TownSortKey.TERRITORIES]: (a, b) => b.territories.length - a.territories.length,
        };

        // sorts in-place from high to low
        nationsAndTowns.sort(nationSortFunctions[sortKey]);

        // within each nation sort by town player count
        for ( const nationData of nationsAndTowns ) {
            nationData.towns.sort(townSortFunctions[sortKey]);
            townsList.push(...nationData.towns);
        }

        // sort remaining towns without nation by player count
        const townsWithoutNation = [];
        for ( const town of Nodes.towns.values() ) {
            if ( town.nation === undefined || town.nation === null ) {
                townsWithoutNation.push(town);
            }
        }

        townsWithoutNation.sort(townSortFunctions[sortKey]);

        townsList.push(...townsWithoutNation);

        return townsList;
    },

    /**
     * Sort a town's residents list order by status: leader, officer, rest...
     * Returns a new array of sorted residents. Caller must manually
     * replace the town's residents array with the returned array.
     */
    _sortTownResidents: (town) => {
        const peasants = []; // un-ranked
        const officers = [];

        for ( const r of town.residents ) {
            // console.log("res", r);
            if ( r.uuid === town.leader ) {
                // skip leader, will handle at end
                continue;
            }
            else if ( town.officers?.includes(r.uuid) ) {
                officers.push(new NodesResident({
                    uuid: r.uuid,
                    name: Nodes.residents.get(r.uuid)?.name,
                    rank: RESIDENT_RANK_OFFICER
                }));
            }
            else {
                peasants.push(new NodesResident({
                    uuid: r.uuid,
                    name: Nodes.residents.get(r.uuid)?.name,
                    rank: RESIDENT_RANK_NONE,
                }));
            }
        }

        const residentsSorted = [];
        if ( town.leader !== undefined && town.leader !== null ) {
            residentsSorted.push(new NodesResident({
                uuid: town.leader,
                name: Nodes.residents.get(town.leader)?.name,
                rank: RESIDENT_RANK_LEADER,
            }));
        }
        residentsSorted.push(...officers);
        residentsSorted.push(...peasants);

        return residentsSorted;
    },

    // =====================================
    // global options functions
    // =====================================

    _setTerritoryCost: (constant, scale) => {
        Nodes.territoryCost.constant = constant;
        Nodes.territoryCost.scale = scale;
        Nodes._calculateAllTerritoryCosts();
        Nodes.renderEditor();

        // update territory rendering if costs are being shown
        if ( Nodes.renderTerritoryCost ) {
            Nodes._updateAllTerritoryElements();
            Nodes.renderWorld();
        }
    },

    // =====================================
    // Setting background image settings
    // =====================================

    /**
     * Src must be string path for url source
     * Name is user readable name for the image
     * (e.g. if image was uploaded from file to data URL, name
     * can be the uploaded image name)
     */
    setBackgroundImage: (src, name) => {
        Nodes.backgroundImageSrc = src;
        Nodes.backgroundImageName = name;

        // set image size if current width/height is 0 before re-rendering
        if ( Nodes.backgroundImageWidth === 0 || Nodes.backgroundImageHeight === 0 ) {
            const img = new Image();
            img.src = src;
            img.onload = () => {
                const width = img.width;
                const height = img.height;
                Nodes.backgroundImageWidth = width;
                Nodes.backgroundImageHeight = height;
                Nodes.backgroundImageEndX = Nodes.backgroundImageOriginX + Nodes.backgroundImageWidth;
                Nodes.backgroundImageEndY = Nodes.backgroundImageOriginY + Nodes.backgroundImageHeight;
                Nodes.renderEditor();
                Nodes.renderWorld();
            }
        }
        else {
            Nodes.renderWorld();
        }
    },

    setBackgroundImageOriginX: (x) => {
        if ( x !== Nodes.backgroundImageOriginX ) {
            Nodes.backgroundImageOriginX = x;
            Nodes.backgroundImageEndX = x + Nodes.backgroundImageWidth;
            Nodes.renderWorld();
        }
    },

    setBackgroundImageOriginY: (y) => {
        if ( y !== Nodes.backgroundImageOriginY ) {
            Nodes.backgroundImageOriginY = y;
            Nodes.backgroundImageEndY = y + Nodes.backgroundImageHeight;
            Nodes.renderWorld();
        }
    },

    setBackgroundImageWidth: (w) => {
        if ( w !== Nodes.backgroundImageWidth ) {
            Nodes.backgroundImageWidth = w;
            Nodes.backgroundImageEndX = Nodes.backgroundImageOriginX + w;
            Nodes.renderWorld();
        }
    },

    setBackgroundImageHeight: (h) => {
        if ( h !== Nodes.backgroundImageHeight ) {
            Nodes.backgroundImageHeight = h;
            Nodes.backgroundImageEndY = Nodes.backgroundImageOriginY + h;
            Nodes.renderWorld();
        }
    },

    // =====================================
    // resource node functions
    // =====================================
    _setDefaultNodeProperties: (data) => {
        Nodes.defaultNodeProperties = data;
        Nodes.defaultNodeString = JSON.stringify(Object.assign(new NodesResource({ name: "null" }), Nodes.defaultNodeProperties), null, 3);
        Nodes.renderEditor();
    },

    _createNode: (name) => {
        let newName = name;
        if ( name === undefined ) {
            // generate new name
            newName = "node0";
            let i = 1;
            while ( Nodes.nodes.has(newName) ) {
                newName = `node${i}`;
                i += 1;
            }
        }
        else if ( Nodes.nodes.has(name) ) {
            return;
        }
        
        const newNode = Object.assign(new NodesResource({ name: newName }), Nodes.defaultNodeProperties);
        Nodes.nodes.set(newName, newNode);
        Nodes.nodesNameList = Array.from(Nodes.nodes.keys());
        Nodes.renderEditor();
    },

    _deleteNode: (name) => {
        if ( Nodes.nodes.has(name) ) {
            Nodes.nodes.delete(name);
            Nodes.nodesNameList = Array.from(Nodes.nodes.keys());
            if ( Nodes.selectedNodeName === name ) {
                Nodes.selectedNodeIndex = undefined;
                Nodes.selectedNodeName = undefined;
            }

            // go through all territories and remove node
            Nodes.territories.forEach((territory, id) => {
                let idx = territory.nodes.indexOf(name);
                if ( idx !== -1 ) {
                    territory.nodes.splice(idx, 1);
                }
            });

            // render update
            Nodes._updateAllTerritoryElements();
            Nodes.renderEditor();
            Nodes.renderWorld();
        }
    },

    _renameNode: (name, newName) => {
        if ( name !== newName && Nodes.nodes.has(name) ) {
            if ( newName !== "" && !Nodes.nodes.has(newName) ) {
                let val = Nodes.nodes.get(name);
                val.name = newName;

                Nodes.nodes.delete(name);
                Nodes.nodes.set(newName, val);
                Nodes.nodesNameList = Array.from(Nodes.nodes.keys());
                
                // go through all territories and rename node
                Nodes.territories.forEach((territory, id) => {
                    let idx = territory.nodes.indexOf(name);
                    if ( idx !== -1 ) {
                        territory.nodes.splice(idx, 1, newName);
                    }
                });

                // force select renamed node (prevents indexing errors)
                Nodes.selectedNodeIndex = Nodes.nodes.size - 1;
                Nodes.selectedNodeName = newName;
                Nodes.renderEditor();
            }
        }
    },

    _setSelectedNode: (name, idx) => {
        Nodes.selectedNodeName = name;
        Nodes.selectedNodeIndex = idx;
        Nodes.renderEditor();
    },

    _getNodeIcon: (name) => {
        if ( Nodes.nodes.has(name) ) {
            return Nodes.nodes.get(name).icon;
        }
        return undefined;
    },

    _setNodeIcon: (name, icon) => {
        if ( Nodes.nodes.has(name) ) {
            Nodes.nodes.get(name).icon = String(icon);

            // render update
            Nodes._updateAllTerritoryElements();
            Nodes.renderEditor();
            Nodes.renderWorld();
        }
    },

    _setNodeData: (name, data) => {
        if ( Nodes.nodes.has(name) ) {
            // save old and new resource node cost values, for checking if we need
            // to re-calculate territory costs
            const oldCost = Nodes.nodes.get(name).cost;
            const newCost = data.cost;
            
            // update resource node data
            Object.assign(Nodes.nodes.get(name), data);
            Nodes.renderEditor();
            
            // check if cost changed, if so need to re-render territories with this node
            if ( oldCost?.scale !== newCost?.scale || oldCost?.constant !== newCost?.constant ) {
                // find territory ids that have this node, update cost and re-render
                const ids = [];
                Nodes.territories.forEach((territory, id) => {
                    if ( territory.nodes.indexOf(name) !== -1 ) {
                        ids.push(id);
                    }
                });
                ids.forEach(id => Nodes._calculateTerritoryCost(id));
                Nodes._updateTerritoryElementIds(ids);
                Nodes.renderWorld();
            }
        }
    },

    // =====================================
    // territory functions
    // -> note internal engine for grid/territory
    //    in wasm memory space
    // =====================================
    _createTerritory: () => {
        // create new territory in wasm shadow realm
        const id = Nodes.wasmWorld.createTerritory();

        // js side territory
        const newTerritory = new NodesTerritory(id);

        // if id already exists, just map id -> new territory
        if ( Nodes.territories.has(id) ) {
            Nodes.territories.set(id, newTerritory);
        }
        // new id, append an empty to territoryElements (will get created
        // in _updateTerritoryElement called by _selectTerritory)
        else {
            Nodes.territoryIdToIndex.set(id, Nodes.territoryElements.length);
            Nodes.territoryElements.push(undefined);
            Nodes.territories.set(id, newTerritory);
        }

        // immediately select created node (will re-render territory jsx elements)
        Nodes._selectTerritory(id, true);

        Nodes.renderEditor();
        Nodes.renderWorld();

        return id;
    },

    /**
     * Delete list of territories
     */
    _deleteTerritory: (ids) => {
        for ( const id of ids ) {
            if ( Nodes.territories.has(id) ) {
                Nodes.wasmWorld.deleteTerritory(id);
                Nodes.territories.delete(id);
                Nodes.selectedTerritories.delete(id);
    
                // update id to index
                let i = 0;
                Nodes.territoryIdToIndex.clear();
                Nodes.territories.forEach( (terr, id) => {
                    Nodes.territoryIdToIndex.set(id, i);
                    i += 1;
                });
    
                if ( Nodes.selectedTerritory !== undefined && Nodes.selectedTerritory.id === id ) {
                    Nodes.selectedTerritory = undefined;
                }

                // update world render
                Nodes._updateAllTerritoryElements();
                Nodes.renderEditor();
                Nodes.renderWorld();
            }
        }
    },

    /**
     * Merges territories in `ids` array into the first index territory (ids[0]).
     */
    _mergeTerritories: (ids) => {
        if ( ids.length <= 1 ) {
            return;
        }

        for ( const id of ids ) {
            if ( !Nodes.territories.has(id) ) {
                console.error("Cannot merge:", id, "does not exist");
                return;
            }
        }

        Nodes.wasmWorld.mergeTerritories(ids);
        Nodes._getTerritoryBorder(ids[0]); // update border

        // remove other ids
        for ( let i = 1; i < ids.length; i++ ) {
            Nodes.territories.delete(ids[i]);
        }

        // set selected territory to merged territory
        Nodes.selectedTerritories.clear();
        Nodes.selectedTerritories.set(ids[0], Nodes.territories.get(ids[0]));
        Nodes.selectedTerritory = Nodes.territories.get(ids[0]);
        Nodes.selectedTerritory.selected = true;

        // update id to index
        let i = 0;
        Nodes.territoryIdToIndex.clear();
        Nodes.territories.forEach( (terr, id) => {
            Nodes.territoryIdToIndex.set(id, i);
            i += 1;
        });

        // update world render
        Nodes._updateAllTerritoryElements();
        Nodes.renderEditor();
        Nodes.renderWorld();

        return ids[0];
    },

    /**
     * Wrapper for merging selected territory ids
     */
    mergeSelectedTerritories: () => {
        console.log("Merging selected territories...");
        let newId = Nodes._mergeTerritories(Nodes.selectedTerritoryIds());
        if ( newId !== undefined ) {
            console.log(`Merged into id=${newId}`);
        }
        else {
            console.log("No merge");
        }
    },

    /**
     * Subdivide selected territory into randomly generated sub territories
     */
    _subdivideIntoRandomTerritories: (
        id,
        averageRadius,
        scaleX,
        scaleY,
        randomSeed,
        iterationsSmoothCenters,
        iterationsSmoothCorners,
        deleteSmallerThan,
        mergeSmallerThan,
        copyName,
    ) => {

        if ( !Nodes.territories.has(id) ) {
            console.error(`Invalid territory ${id}`);
            return;
        }

        let newIds = Nodes.wasmWorld.subdivideIntoRandomTerritories(
            id,
            Math.max(0, averageRadius),
            Math.max(0, scaleX),
            Math.max(0, scaleY),
            randomSeed,
            Math.max(0, iterationsSmoothCenters),
            Math.max(0, iterationsSmoothCorners),
            Math.max(0, deleteSmallerThan),
            Math.max(0, mergeSmallerThan),
        );

        // delete old territory
        const oldName = Nodes.territories.get(id).name;
        Nodes.territories.delete(id);
        Nodes.selectedTerritories.clear();

        newIds.forEach(id => {
            // js side territory
            const newTerritory = new NodesTerritory(id);
            if ( copyName ) {
                newTerritory.name = oldName;
            }
            newTerritory.size = Nodes.wasmWorld.getTerritorySize(id);
            Nodes.territories.set(id, newTerritory);
            Nodes._getTerritoryBorder(id); // update border

            // select territory
            newTerritory.selected = true;
            Nodes.selectedTerritories.set(id, newTerritory);
        });

        // update id to index
        let i = 0;
        Nodes.territoryIdToIndex.clear();
        Nodes.territories.forEach( (terr, id) => {
            Nodes.territoryIdToIndex.set(id, i);
            i += 1;
        });

        if ( Nodes.selectedTerritory !== undefined && Nodes.selectedTerritory.id === id ) {
            Nodes.selectedTerritory = undefined;
        }

        // update world render
        Nodes._updateAllTerritoryElements();
        Nodes.renderEditor();
        Nodes.renderWorld();

        // return list of new ids
        return newIds;
    },

    /**
     * handle behavior when selecting territory
     * - if in nodes panel, select territory also
     *   assigns/removes resource node
     * - else, select territory sets selected territory
     *   for editing/display info
     */
    _selectTerritory: (id, allowWhilePainting = false) => {
        // cancel if id same
        if ( id === Nodes.selectedTerritory?.id && Nodes.isEditingTerritoryNodes === false ) {
            return;
        }

        // cancel while painting
        if ( Nodes.enabledPainting && !allowWhilePainting ) {
            return;
        }
        
        // update current selected territory
        if ( Nodes.selectedTerritory !== undefined ) {
            const selectedId = Nodes.selectedTerritory.id;
            Nodes.selectedTerritory = undefined;
            Nodes._updateTerritoryElement(selectedId);
        }

        if ( Nodes.territories.has(id) ) {
            let territory = Nodes.territories.get(id);

            // set to main selected territory
            Nodes.selectedTerritory = territory;

            // if shift not selected, clear previous selection
            let idsToUpdate = undefined;
            if ( Nodes.shiftKey === false ) {
                idsToUpdate = [id]
                for ( let terr of Nodes.selectedTerritories.values() ) {
                    idsToUpdate.push(terr.id)
                    terr.selected = false;
                }
                Nodes.selectedTerritories.clear();
            }

            territory.selected = true;
            Nodes.selectedTerritories.set(id, territory);

            // if territory has a town, also select that town
            if ( territory.town !== undefined ) {
                // search for town object in towns
                let index = 0;
                for ( const town of Nodes.townsList ) {
                    if ( territory.town === town ) {
                        Nodes.selectedTown = town;
                        Nodes.selectedTownIndex = index;
                        break;
                    }
                    index += 1;
                }
            }

            // if assigning nodes, add/remove node from territory
            if ( Nodes.isEditingTerritoryNodes === true && Nodes.selectedNodeName !== undefined && Nodes.ctrlKey === true ) {
                const nodeName = Nodes.selectedNodeName;
                if ( nodeName !== undefined ) {
                    // if already has, remove
                    const index = territory.nodes.indexOf(nodeName);
                    if ( index !== -1 ) {
                        territory.nodes.splice(index, 1);
                    }
                    else { // add node
                        territory.nodes.push(nodeName);
                    }
                }
            }

            if ( idsToUpdate !== undefined ) {
                Nodes._updateTerritoryElementIds(idsToUpdate);
            }
            else {
                Nodes._updateTerritoryElement(id);
            }
            Nodes.renderEditor();
            Nodes.renderWorld();
        }
    },

    _deselectTerritory: () => {
        if ( Nodes.selectedTerritory !== undefined ) {
            const selectedIds = [Nodes.selectedTerritory.id];

            // deselect all territories
            for ( let terr of Nodes.selectedTerritories.values() ) {
                selectedIds.push(terr.id)
                terr.selected = false;
            }
            Nodes.selectedTerritories.clear();
            Nodes.selectedTerritory = undefined;

            Nodes._updateTerritoryElementIds(selectedIds);
            Nodes.renderEditor();
            Nodes.renderWorld();
        }
    },

    /**
     * Get array of selected territory ids
     */
    selectedTerritoryIds: () => {
        const ids = new Array(Nodes.selectedTerritories.size);
        let i = 0;
        for ( const v of Nodes.selectedTerritories.values() ) {
            ids[i] = v.id;
            i += 1;
        }
        return ids;
    },
    
    // set territory name (non-unique)
    _setTerritoryName: (territory, newName) => {
        if ( territory !== undefined ) {
            territory.name = newName;
        }
    },

    // set state for editing nodes during territory select
    // adds/removes node from territory on select
    _setEditingTerritoryNodes: (state) => {
        Nodes.isEditingTerritoryNodes = state;
    },

    _addNodeToTerritory: (id, nodeName) => {
        if ( Nodes.territories.has(id) && Nodes.nodes.has(nodeName) ) {
            let territory = Nodes.territories.get(id);
            if ( !territory.nodes.includes(nodeName) ) {
                territory.nodes.push(nodeName);

                Nodes._updateTerritoryElement(id);
                Nodes.renderEditor();
                Nodes.renderWorld();

                return true; // success
            }
        }
        return false; // failed
    },

    _removeNodeFromTerritory: (id, nodeName) => {
        if ( Nodes.territories.has(id) && Nodes.nodes.has(nodeName) ) {
            let territory = Nodes.territories.get(id);
            let idx = territory.nodes.indexOf(nodeName);
            if ( idx !== -1 ) {
                territory.nodes.splice(idx, 1);

                Nodes._updateTerritoryElement(id);
                Nodes.renderEditor();
                Nodes.renderWorld();

                return true; // success
            }
        }
        return false; // failed
    },

    /**
     * Remove all nodes from array of territory ids
     */
    _removeAllNodesFromTerritories: (ids) => {
        for ( const id of ids ) {
            if ( Nodes.territories.has(id) ) {
                let territory = Nodes.territories.get(id);
                territory.nodes = [];
            }
        }

        Nodes._updateTerritoryElementIds(ids);
        Nodes.renderEditor();
        Nodes.renderWorld();
    },

    /**
     * Randomly distribute set of nodes in a group of territories.
     * This function will only append additional node into territories.
     * `nodes` is a dict of nodes with corresponding data:
     *   resources = {
     *        "gold": 0.5,
     *     "iron": 0.2,
     *   }
     * The number is the weight probability. Weights are re-normalized
     * so the total probability is 1.
     */
    _distributeResourcesInTerritories: (ids, randomSeed, resources) => {
        if ( ids.length === 0 ) {
            return;
        }

        // convert resources to array of resources and probabilities
        let prTotal = 0.0;
        const rsrc = [];
        const pr = [];
        
        for ( const r in resources ) {
            if (Nodes.nodes.has(r) ) {
                rsrc.push(r);
                pr.push(resources[r]);
                prTotal += resources[r];
            }
            else {
                console.error(`No resource ${r}, skipping`);
            }
        }

        if ( pr.length === 0 ) {
            return;
        }

        // if sum less than 1.0, inject an "empty" resource
        if ( prTotal < 1.0 ) {
            rsrc.push(undefined);
            pr.push(1.0 - prTotal)
        }

        let sampler = IndexSampler.fromWeights(randomSeed, pr);

        for ( const id of ids ) {
            if ( Nodes.territories.has(id) ) {
                let territory = Nodes.territories.get(id);
                let r = rsrc[sampler.sample()];
                if ( r !== undefined && !territory.nodes.includes(r) ) {
                    territory.nodes.push(r);
                }
            }
        }

        Nodes._updateTerritoryElementIds(ids);
        Nodes.renderEditor();
        Nodes.renderWorld();
    },

    /**
     * Wrapper to distribute resources in selected territories
     * with global saved distribution settings
     */
    distributeResourcesInSelectedTerritories: () => {
        Nodes._distributeResourcesInTerritories(
            Nodes.selectedTerritoryIds(),
            Nodes.resourceDistributeRandomSeed,
            Nodes.resourceDistributeSettings,
        )
    },
    
    // add an array buffer [x1, y1, x2, y2, ... xN, yN]
    // of coordinates to a territory
    _addCoordsToTerritory: (id, coordsBuffer) => {
        if ( Nodes.territories.has(id) && coordsBuffer !== undefined && coordsBuffer.length > 0 ) {
            let status = Nodes.wasmWorld.addCoordsToTerritory(id, coordsBuffer);
            if ( status === true ) { // success
                Nodes.territories.get(id).size = Nodes.wasmWorld.getTerritorySize(id);
                Nodes._getTerritoryBorder(id); // update border

                // render
                Nodes._updateTerritoryElement(id);
                Nodes.renderEditor();
                Nodes.renderWorld();
            }
        }
    },

    // add a circle of chunks centered at center = {x: x, y: y}
    // with radius in chunks
    _addCircleToTerritory: (id, x, y, radius) => {
        // console.log("ADD CIRCLe", id, center, radius);
        if ( Nodes.territories.has(id) ) {
            let status = Nodes.wasmWorld.addCircleToTerritory(id, x, y, radius);
            // console.log(status);
            if ( status === true ) { // success
                Nodes.territories.get(id).size = Nodes.wasmWorld.getTerritorySize(id);
                Nodes._getTerritoryBorder(id); // update border

                // render
                Nodes._updateTerritoryElement(id);
                Nodes.renderEditor();
                Nodes.renderWorld();
            }
        }
    },

    // add a circle of chunks centered at center = {x: x, y: y}
    // with radius in chunks
    _removeCircleToTerritory: (id, x, y, radius) => {
        // console.log("ADD CIRCLe", id, center, radius);
        if ( Nodes.territories.has(id) ) {
            let status = Nodes.wasmWorld.removeCircleToTerritory(id, x, y, radius);
            // console.log(status);
            if ( status === true ) { // success
                Nodes.territories.get(id).size = Nodes.wasmWorld.getTerritorySize(id);
                Nodes._getTerritoryBorder(id); // update border

                // render
                Nodes._updateTerritoryElement(id);
                Nodes.renderEditor();
                Nodes.renderWorld();
            }
        }
    },

    _listTerritories: () => {
        Nodes.wasmWorld.listTerritories();
    },

    // get and save territory border
    // -> parse wasm territory border buffer
    _getTerritoryBorder: (id) => {
        // console.log("GETTING BORDER");
        // let t1 = performance.now();
        let buffer = Nodes.wasmWorld.getTerritoryBorder(id);
        // let t2 = performance.now();
        // console.log(`Time:${t2 - t1}`);

        // parse buffer
        let core = {x: buffer[0], y: buffer[1]}; // IN MINECRAFT COORDS
        let borders = [];
        let numClusters = buffer[2];
        let bufOffset = 3;
        for ( let i = 0; i < numClusters; i++ ) {
            let numChunks = buffer[bufOffset];
            let edgeLoopPoints = buffer[bufOffset + 1];
            let idxChunksStart = bufOffset + 2;
            let idxChunksEnd = idxChunksStart + 2*numChunks;
            let idxEdgeLoopStart = idxChunksEnd;
            let idxEdgeLoopEnd = idxEdgeLoopStart + 2*edgeLoopPoints;
            borders.push({
                numChunks: numChunks,
                numEdgeLoopPoints: edgeLoopPoints,
                chunks: buffer.subarray(idxChunksStart, idxChunksEnd),
                edge: buffer.subarray(idxEdgeLoopStart, idxEdgeLoopEnd),
            });

            bufOffset = idxEdgeLoopEnd;
        }
        
        // set territory border and core
        let territory = Nodes.territories.get(id);
        territory.core = core;
        territory.coreChunk = {x: Math.floor(core.x/16), y: Math.floor(core.y/16)};
        territory.borders = borders;
    },

    _getLatLngFromCoord: (x, z) => {
        // dynmap projection
        if ( Nodes.dynmap !== undefined ) {
            const projection = Nodes.dynmap.getProjection();

            // get svg point x,z from world point x,z
            let point;
            if ( projection !== undefined ) {
                point = projection.fromLocationToLatLng({
                    x: x,
                    y: 0,
                    z: z,
                });
                point = Nodes.dynmap.map.latLngToLayerPoint(point);
            }
            else {
                point = {lat: x, lng: z};
            }
            return point;
        }
        
        return {lat: undefined, lng: undefined}; // arbitrary
    },
    
    _createTerritoryJsx: (terr) => {
        const isMainSelected = Nodes.selectedTerritory !== undefined && Nodes.selectedTerritory.id === terr.id;

        return <Territory
            key={terr.id}
            territory={terr}

            // render options
            renderTerritoryIcons={Nodes.renderTerritoryIcons}
            renderTerritoryId={Nodes.renderTerritoryId}
            renderTerritoryCost={Nodes.renderTerritoryCost}
            renderTerritoryOpaque={Nodes.renderTerritoryOpaque}
            renderTerritoryColors={Nodes.renderTerritoryColors}
            renderTerritoryNoBorders={Nodes.renderTerritoryNoBorders}

            selected={terr.selected}
            isMainSelected={isMainSelected}
            selectTerritory={Nodes._selectTerritory}
            getPoint={Nodes._getLatLngFromCoord}
            getNodeIcon={Nodes._getNodeIcon}
            resourceIcons={Nodes.resourceIcons}
        />;
    },
    
    // re-render single territory element, update that single entry
    // in territory elements array with new object
    _updateTerritoryElement: (id) => {
        const territoryElements = Nodes.territoryElements.slice();

        // get territory elements array index
        const index = Nodes.territoryIdToIndex.get(id);
        if ( index === undefined ) {
            return;
        }

        if ( index < territoryElements.length ) {
            let terrElement = territoryElements[index];
            if ( terrElement !== undefined && id !== terrElement.props.territory.id ) {
                console.error("UPDATE TERRITORY ELEMENT ID DOES NOT MATCH", id, terr);
                return;
            }

            // create new territory element
            const terr = Nodes.territories.get(id);
            if ( terr === undefined || terr.borders === undefined ) {
                return;
            }

            territoryElements[index] = Nodes._createTerritoryJsx(terr);

            // update nodes territory elements
            Nodes.territoryElements = territoryElements;
        }
        else {
            console.error("new territory index out of bounds?");
            return;
        }
    },

    /**
     * Re-render array of ids
     */
    _updateTerritoryElementIds: (ids) => {
        const territoryElements = Nodes.territoryElements.slice();

        ids.forEach(id => {
            // get territory elements array index
            const index = Nodes.territoryIdToIndex.get(id);
            if ( index === undefined ) {
                return;
            }

            if ( index < territoryElements.length ) {
                let terrElement = territoryElements[index];
                if ( terrElement !== undefined && id !== terrElement.props.territory.id ) {
                    console.error("UPDATE TERRITORY ELEMENT ID DOES NOT MATCH", id, terr);
                    return;
                }

                // create new territory element
                const terr = Nodes.territories.get(id);
                if ( terr === undefined || terr.borders === undefined ) {
                    return;
                }

                territoryElements[index] = Nodes._createTerritoryJsx(terr);
            }
            else {
                console.error("new territory index out of bounds?");
                return;
            }
        });

        // update nodes territory elements
        Nodes.territoryElements = territoryElements;
    },

    // re-render all territory elements
    // TODO: better caching
    _updateAllTerritoryElements: () => {
        const territoryElements = [];
        Nodes.territories.forEach(terr => {
            if ( terr.borders !== undefined ) {
                territoryElements.push(Nodes._createTerritoryJsx(terr));
            }
            else {
                territoryElements.push(undefined);
            }
        });

        Nodes.territoryElements = territoryElements;
    },

    // create array of stripe patterns to fill occupied territories
    // 1. background fill color = town"s color
    // 2. stripe color = occupier"s color
    // also create "-dark" variant for selected territories
    _updateStripePatterns: () => {
        // set of string ids for colors patterns already generated
        const generatedStripePatternIds = new Set();

        // generate stripe patterns (must be unique)
        const stripePatterns = []
        Nodes.towns.forEach(town => {
            if ( town.captured?.length > 0 ) {
                // current town is stripe color
                const stripeColorRGB = town.color;

                town.captured.forEach(terrId => {
                    const territory = Nodes.territories.get(terrId);
                    const terrTown = territory?.town;
                    if ( terrTown !== undefined ) {
                        // get territory town
                        const terrTown = territory.town;

                        // territory town is fill color
                        const fillColorRGB = terrTown.color;

                        // check if this color pattern exists
                        const stripePatternId = `stripes-${fillColorRGB[0]}-${fillColorRGB[1]}-${fillColorRGB[2]}-${stripeColorRGB[0]}-${stripeColorRGB[1]}-${stripeColorRGB[2]}`;

                        if ( !generatedStripePatternIds.has(stripePatternId) ) {
                            const fillColor = `rgb(${fillColorRGB[0]}, ${fillColorRGB[1]}, ${fillColorRGB[2]})`;
                            const stripeColor = `rgb(${stripeColorRGB[0]}, ${stripeColorRGB[1]}, ${stripeColorRGB[2]})`;

                            const fillColorDark = `rgb(${fillColorRGB[0]/1.5}, ${fillColorRGB[1]/1.5}, ${fillColorRGB[2]/1.5})`;
                            const stripeColorDark = `rgb(${stripeColorRGB[0]/1.5}, ${stripeColorRGB[1]/1.5}, ${stripeColorRGB[2]/1.5})`;

                            stripePatterns.push(
                                <StripePattern
                                    id={stripePatternId}
                                    key={stripePatternId}
                                    fillColor={fillColor}
                                    stripeColor={stripeColor}
                                />
                            );
                            stripePatterns.push(
                                <StripePattern
                                    id={"dark-" + stripePatternId}
                                    key={"dark-" + stripePatternId}
                                    fillColor={fillColorDark}
                                    stripeColor={stripeColorDark}
                                />
                            );

                            // mark pattern
                            generatedStripePatternIds.add(stripePatternId)
                        }
                    }
                });
            }
            
        });
        Nodes.stripePatterns = stripePatterns
    },

    /**
     * Create svg element for town names. These are centered
     * on the "home" territory of each town.
     */
     _createTownNameTagJsx: (town) => {
        let territory = Nodes.territories.get(town.home);
        if ( territory === undefined || territory === null ) {
            // console.error(`Town ${town.name} has no home territory?`); // avoid console spam
            return null;
        }

        // core coordinate
        const core = Nodes._getLatLngFromCoord(territory.core.x, territory.core.y);

        let textOriginX = core.x;
        let textOriginY = core.y;

        // offset if rendering capital icons
        if ( Nodes.renderTerritoryCapitals ) {
            textOriginY -= 8;
        }

        // tag name: either town or nation name
        let tagName;
        if ( Nodes.renderTownNames === RENDER_TOWN_NAMETAG_TOWN ) {
            tagName = town.name;
        } else if ( Nodes.renderTownNames === RENDER_TOWN_NAMETAG_NATION ) {
            const nation = town.nation;
            if ( nation !== undefined ) {
                // only display nation name for capital of a nation,
                // otherwise skip this town name tag
                if ( town.name !== Nodes.nations.get(nation)?.capital ) {
                    return null;
                }
                tagName = town.nation;
            }
            else { // for town without nation, use its name
                tagName = town.name;
            }
        } else {
            console.error(`Invalid town name render mode: ${Nodes.renderTownNames}`);
            return null;
        }

        // also replace "_" with " "
        // since no spaces allowed in names, common for players to use "_" as a "space"
        tagName = tagName.replaceAll("_", " ");

        return <g key={town.home}>
            <text
                x={textOriginX}
                y={textOriginY}
                textRendering="optimizeSpeed"
                fill={"#000"}
                textAnchor={"middle"}
                style={{ font: "bold 24px serif", stroke: "#fff", strokeWidth: "1pt", paintOrder: "stroke" }}
            >
                {tagName}
            </text>
        </g>;
    },

    /**
     * Re-create all svg element for town names. This creates
     * a town name text tag centered on a town's home territory
     * for each town.
     */
     _updateAllTownNameTagJsx: () => {
        let elements = [];

        if ( Nodes.renderTownNames !== RENDER_TOWN_NAMETAG_NONE ) {
            for ( const town of Nodes.towns.values() ) {
                const jsx = Nodes._createTownNameTagJsx(town);
                if ( jsx !== null ) {
                    elements.push(jsx);
                }
            }
        }

        Nodes.townNameElementsJsx = elements;
    },

    /**
     * Create svg elements for town capitals.
     */
     _createTownCapitalJsx: (town) => {
        let territory = Nodes.territories.get(town.home);
        if ( territory === undefined || territory === null ) {
            // console.error(`Town ${town.name} has no home territory?`); // avoid console spam
            return null;
        }

        // core coordinate
        const core = Nodes._getLatLngFromCoord(territory.core.x, territory.core.y);

        return <g key={town.home}>
            <image x={core.x - 9} y={core.y - 9} width={18} height={18} href={IconMapCapital}/>
        </g>;
    },

    /**
     * Re-create all svg element for town names. This creates
     * a town name text tag centered on a town's home territory
     * for each town.
     */
     _updateAllTownCapitalsJsx: () => {
        let elements = [];

        if ( Nodes.renderTerritoryCapitals === true ) {
            for ( const town of Nodes.towns.values() ) {
                const jsx = Nodes._createTownCapitalJsx(town);
                elements.push(jsx);
            }
        }

        Nodes.townCapitalElementsJsx = elements;
    },

    /**
     * Create port element jsx
     */
    _createPortJsx: (port) => {
        return <Port
            key={port.name}
            port={port}
            x={port.x}
            z={port.z}
            showPortTooltip={Nodes._showPortTooltip}
            removePortTooltip={Nodes._removePortTooltip}
            getPoint={Nodes._getLatLngFromCoord}
        />;
    },

    /**
     * Show port tooltip
     */
    _showPortTooltip: (port, cx, cy, enable = true) => {
        const props = {
            enable: enable,
            clientX: cx,
            clientY: cy,
            port: port,
        };
        Nodes.portTooltipContainerRoot?.render(<PortTooltip {...props}/>);
    },

    /**
     * Removes port tooltip
     */
    _removePortTooltip: () => {
        Nodes._showPortTooltip(undefined, 0, 0, false);
    },

    /**
     * Create elements for all ports
     */
    _updateAllPortJsx: () => {
        let elements = [];
        for ( const port of Nodes.ports.values() ) {
            const portJsx = Nodes._createPortJsx(port);
            elements.push(portJsx);
        }

        Nodes.portsJsx = elements;
    },

    // calculate all territory costs
    _calculateAllTerritoryCosts: () => {
        const base = Nodes.territoryCost.constant;
        const perChunkScale = Nodes.territoryCost.scale;

        Nodes.territories.forEach( terr => {
            // initialize with global cost rates
            let costConstant = base;
            let costScale = perChunkScale;

            terr.nodes.forEach( type => {
                const resource = Nodes.nodes.get(type);
                if ( resource !== undefined ) {
                    costConstant += resource.cost.constant;
                    costScale *= resource.cost.scale;
                }
            });

            const chunks = terr.size;
            const cost = Math.round(costConstant + costScale * chunks);
            terr.cost = cost;
        });
    },

    // calculate territory cost for specific territory id
    _calculateTerritoryCost: (id) => {
        const terr = Nodes.territories.get(id);
        if ( terr !== undefined ) {
            // initialize with global cost rates
            let costConstant = Nodes.territoryCost.constant;
            let costScale = Nodes.territoryCost.scale;

            terr.nodes.forEach( type => {
                const resource = Nodes.nodes.get(type);
                if ( resource !== undefined ) {
                    costConstant += resource.cost.constant;
                    costScale *= resource.cost.scale;
                }
            });

            const chunks = terr.size;
            const cost = Math.round(costConstant + costScale * chunks);
            terr.cost = cost;
        }
    },

    // internal debug function for generating/rendering territory colors
    _color: () => {
    
        // calculate neighbors
        console.log("calculating neighbors");
        Nodes.wasmWorld.calculateNeighbors();

        // generate colors
        console.log("generating colors")
        Nodes.wasmWorld.generateColors();

        console.log("verifying color solution");
        
        let passed = true;

        // gather colors and neighbors, check if any missing
        Nodes.territories.forEach(terr => {
            terr.neighbors = Array.from(Nodes.wasmWorld.getTerritoryNeighbors(terr.id));
            terr.color = Nodes.wasmWorld.getTerritoryColor(terr.id);

            if ( terr.color == undefined || terr.color == null ) {
                console.error(`terr ${terr.id}: has no color`);
                passed = false;
            }
        });

        // check if solution correct: no neighbors have identical colors
        Nodes.territories.forEach( (terr, _) => {
            terr.neighbors.forEach(neighborId => {
                const neighbor = Nodes.territories.get(neighborId);
                if ( terr.color == neighbor.color ) {
                    console.error(`terr ${terr.id} - ${neighbor.id}: same color ${terr.color}`);
                    passed = false;
                }
            });
        });

        if ( passed ) {
            console.log("PASSED COLOR CHECK");
        }

        // render colors
        Nodes.renderTerritoryColors = true;
        Nodes._updateAllTerritoryElements();
        Nodes.renderWorld();
    },

    // private internal test fun, calculate borders
    // and set territories isEdge property
    _calculateTerritoriesAtEdge: () => {
        // calculate neighbors
        console.log("calculating neighbors");
        Nodes.wasmWorld.calculateNeighbors();

        // set isEdge
        Nodes.territories.forEach(terr => {
            const isEdge = Nodes.wasmWorld.getTerritoryIsEdge(terr.id);
            terr.isEdge = isEdge;
            
            // set territory color
            terr.color = isEdge ? 1 : 0;
        });

        // render colors
        Nodes.renderTerritoryColors = true;
        Nodes._updateAllTerritoryElements();
        Nodes.renderWorld();
    },

    _getTerritoriesInAABB: (xmin, zmin, xmax, zmax, select = true) => {
        // calculate neighbors
        console.log(`Getting territories from (${xmin},${zmin}) to (${xmax},${zmax})`);
        const terrIds = Nodes.wasmWorld.getTerritoriesInAABB(xmin, zmin, xmax, zmax);
        
        if ( select ) {
            for ( const id of terrIds ) {
                const terr = Nodes.territories.get(id);
                if ( terr !== undefined ) {
                    terr.selected = true;
                }
            }
            Nodes._updateTerritoryElementIds(terrIds);
            Nodes.renderWorld();
        }

        return terrIds;
    },

    // =====================================
    // town functions
    // =====================================

    _selectTown: (town, index) => {
        Nodes.selectedTown = town;
        Nodes.selectedTownIndex = index;
        Nodes.renderEditor();
        Nodes.renderWorld();
    },

    // =====================================
    // painting functions
    // =====================================
    _setPainting: (state) => {
        Nodes.enabledPainting = state;
        Nodes.renderWorld();
    },

    _togglePainting: () => {
        Nodes.enabledPainting = !Nodes.enabledPainting;
        Nodes.renderWorld();
    },

    _startPaint: () => {
        Nodes.isPainting = true;
        if ( Nodes.enabledPainting ) {
            Nodes._paint();
        }
    },

    _stopPaint: () => {
        Nodes.isPainting = false;
    },

    _paint: () => {
        if ( Nodes.isPainting && Nodes.selectedTerritory !== undefined ) {
            if ( Nodes.ctrlKey ) { // remove chunks
                Nodes._removeCircleToTerritory(Nodes.selectedTerritory.id, Nodes.chunkX, Nodes.chunkY, Nodes.paintRadius);
            }
            else { // add chunks
                Nodes._addCircleToTerritory(Nodes.selectedTerritory.id, Nodes.chunkX, Nodes.chunkY, Nodes.paintRadius);
            }
        }
    },
};

// export to webpack
export default Nodes;
