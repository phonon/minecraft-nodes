/**
 * SVG render of a single territory element
 * Rendering performed externally by nodes for memoization
 * -> WorldRenderer recieves list of Territory elements
 */

'use strict';

const ICON_SIZE_MAX = 32;
const ICON_SIZE_MIN = 8;

// assigned territory colors from graph coloring
const COLORS = [
    '#f00',
    '#0f0',
    '#00f',
    '#ff0',
    '#0ff',
    '#f0f',
];

// territory svg chunk
export const Territory = (props) => {
    let territory = props.territory;
    let getPoint = props.getPoint;

    // core coordinate
    const core = getPoint(territory.core.x, territory.core.y);

    // svg border outline paths
    let paths = [];
    territory.borders.forEach(b => {
        let e = b.edge;
        let pnt = getPoint(e[0], e[1]);
        let p = `M ${pnt.x} ${pnt.y}`;
        for ( let i = 2; i < e.length-2; i += 2 ) {
            pnt = getPoint(e[i], e[i+1]);
            p += `L ${pnt.x} ${pnt.y}`
        }
        p += 'z';
        paths.push(p);
    });
    
    // =======================
    // icon grid sizing
    // -> used for both icon grid placement and territory cost placement
    // =======================
    const icons = territory.nodes.map(nodeName => props.getNodeIcon(nodeName));

    // get icon size and apply size constraint 
    const iconSizeFromMapScale = getPoint(16, 0).x - getPoint(0, 0).x;
    // const iconSize = Math.max(Math.min(1.25 * iconSizeFromMapScale, ICON_SIZE_MAX), ICON_SIZE_MIN);
    // manually set iconSize
    let iconSize = 8;
    if ( iconSizeFromMapScale === 4 ) {
        iconSize = 10;
    }
    else if ( iconSizeFromMapScale === 8 ) {
        iconSize = 14;
    }
    else if ( iconSizeFromMapScale === 16 ) {
        iconSize = 18;
    }
    else if ( iconSizeFromMapScale === 32 ) {
        iconSize = 24;
    }
    else if ( iconSizeFromMapScale > 32 ) {
        iconSize = 32;
    }

    // generate icon grid: fit icons in (n,n) grid, then re-center
    // (note svg images rendered from (0,0) outwards, so grid origin is
    // top-left corner of (0,0) grid index)
    const numIcons = icons.length;
    const iconGridSizeX = Math.ceil(Math.sqrt(icons.length));
    const iconGridSizeY = ( iconGridSizeX === 0 ) ? 0 : Math.ceil(numIcons / iconGridSizeX);
    const gridOrigin = {
        x: core.x - iconSize * iconGridSizeX/2,
        y: core.y - iconSize * iconGridSizeY/2,
    };

    // =======================
    // territory icons
    // =======================
    let icon_grid = undefined;
    if ( props.renderTerritoryIcons === true ) {

        // icon grid origin for cell [0,0] is core - bbox_size/2 + cell_extent/2
        // fill in grid row major order
        const icon_positions = [];
        for ( let i = 0; i < icons.length; i++ ) {
            icon_positions.push({
                x: gridOrigin.x + (i % iconGridSizeX) * iconSize,
                y: gridOrigin.y + Math.floor(i / iconGridSizeX) * iconSize,
            });
        }

        // generate icon grid
        icon_grid = icons.map((ic, i) => 
            <image key={i} x={icon_positions[i].x} y={icon_positions[i].y} width={iconSize} height={iconSize} href={props.resourceIcons.get(ic)}/>
        );
    }

    // get color and opacity based on town
    let color = '#000';
    let fillOpacity = 0;
    let strokeColor = '#000';
    let strokeWidth = 2;

    // override, rendering territory built-in color
    if ( props.renderTerritoryColors && territory.color !== undefined ) {
        color = COLORS[territory.color];
        fillOpacity = 0.5;
    }
    // territory belongs to town, render town color
    else if ( territory.town !== undefined && territory.town.color !== undefined ) {
        let townColor = territory.town.color; // [r, g, b] format

        // occupied territory, use stripe fill
        if ( territory.occupier !== undefined ) {
            // get occupier color
            let occupierColor = territory.occupier.color;

            // get pattern id
            // selected: darken color + more opaque
            if ( props.selected === true ) {
                color = `url(#dark-stripes-${townColor[0]}-${townColor[1]}-${townColor[2]}-${occupierColor[0]}-${occupierColor[1]}-${occupierColor[2]})`;

                // option to use more solid opacity
                if ( props.renderTerritoryOpaque == true ) {
                    fillOpacity += 0.1;
                }
                else {
                    fillOpacity += 0.25;
                }
            }
            else {
                color = `url(#stripes-${townColor[0]}-${townColor[1]}-${townColor[2]}-${occupierColor[0]}-${occupierColor[1]}-${occupierColor[2]})`;
            }
        }
        // use town color
        else {
            // selected: darken color + more opaque
            if ( props.selected === true ) {
                color = `rgb(${townColor[0]/2}, ${townColor[1]/2}, ${townColor[2]/2})`;

                // option to use more solid opacity
                if ( props.renderTerritoryOpaque == true ) {
                    fillOpacity += 0.1;
                }
                else {
                    fillOpacity += 0.25;
                }
            }
            else {
                color = `rgb(${townColor[0]}, ${townColor[1]}, ${townColor[2]})`;
            }
        }

        // option to use more solid opacity
        if ( props.renderTerritoryOpaque == true ) {
            fillOpacity += 0.75;
        }
        else {
            fillOpacity += 0.27;
        }

        // make stroke color darker version of main color
        strokeColor = `rgb(${townColor[0]/3}, ${townColor[1]/3}, ${townColor[2]/3})`;

        // if this territory is town home, use thicker stroke
        if ( territory.town.home === territory.id ) {
            strokeWidth = 4;
        }
    }
    // arbitrary territory
    else {
        if ( props.selected === true ) {
            fillOpacity += 0.2;
        }
        if ( props.isMainSelected === true ) {
            fillOpacity += 0.15;
        }
    }

    // no borders mode
    if ( props.renderTerritoryNoBorders === true ) {
        strokeWidth = 0;
    }
    
    // =======================
    // id and cost text settings
    // =======================
    let textOriginX = core.x;
    let textOriginY = ( props.renderTerritoryIcons === true ) ? gridOrigin.y : core.y;

    let textTerritoryId;
    if ( props.renderTerritoryId === true ) {
        if ( props.renderTerritoryCost === true ) {
            textTerritoryId = `${territory.id}:`
        }
        else {
            textTerritoryId = `${territory.id}`
        }
    }
    else if ( props.renderTerritoryCost === true ) {
        textOriginX = gridOrigin.x;
    }

    // event handlers
    const handleMouseDown = (e) => {
        if ( e.button === 2 ) { // right click only
            props.selectTerritory(territory.id)
        }
    };
    
    return (
        <g
            key={territory.id}
            onMouseDown={handleMouseDown}
        >
            <g>
                {paths.map( (p,i) => 
                    <path
                        key={i}
                        strokeLinejoin="round"
                        strokeLinecap="round"
                        fillRule="evenodd"
                        stroke={strokeColor}
                        strokeOpacity="0.7"
                        strokeWidth={strokeWidth}
                        fill={color}
                        fillOpacity={fillOpacity}
                        d={p}
                    />
                )}
                <text x="0" y="0">TOPKEK</text>
            </g>
            
            {props.renderTerritoryIcons ?
                <g>
                    {icon_grid}
                </g>
            : (null)
            }
            

            {props.renderTerritoryCost || props.renderTerritoryId ?
                <g>
                    {props.renderTerritoryId ? 
                        <text
                            x={textOriginX}
                            y={textOriginY}
                            textRendering="optimizeSpeed"
                            fill={"#900"}
                            textAnchor={"end"}
                            style={{ font: "bold 18px sans-serif" }}
                        >
                            {textTerritoryId}
                        </text>
                        : (null)
                    }
                    {props.renderTerritoryCost ?
                        <text
                            x={textOriginX}
                            y={textOriginY}
                            textRendering="optimizeSpeed"
                            style={{ font: "bold 18px sans-serif" }}
                        >
                            {territory.cost}
                        </text>
                        : (null)
                    }
                </g>
            : (null)
            }
        </g>
    )
};