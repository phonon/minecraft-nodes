/**
 * SVG render layer on top of dynmap renderer
 * 
 * TODO: find way to set map width/height
 * because width/height taken from dynmap, if dynmap has no existing
 * object layers, itll set width=height=0, which makes editor width=height=0
 * -> Must calculate width/height separately and set for the editor
 * 
 */
import { useMemo, useCallback } from "react";
import Nodes from "../nodes";
import {Territory} from "world/territory.jsx";
import "world/css/nodes.css";

export const WorldRenderer = (props) => {

    // dynmap projection
    let projection;
    if ( props.dynmap !== undefined ) {
        projection = props.dynmap.getProjection();
    }
    
    // get svg point x,z from world point x,z
    const getPoint = (x,z) => {
        let point;
        if ( projection !== undefined ) {
            point = projection.fromLocationToLatLng({
                x: x,
                y: 0,
                z: z,
            });
            point = props.dynmap.map.latLngToLayerPoint(point);
        }
        else {
            point = {x: x, z: z};
        }
        return point;
    };

    
    // generate cursor circle if painting
    let cursorCircle = (null);
    if ( props.enabledPainting && props.dynmap !== undefined ) {
        try {
            const cursorCenter = props.dynmap.map.latLngToLayerPoint(props.cursorLatLng);
            const cx = cursorCenter.x;
            const cy = cursorCenter.y;
            // radius in CHUNKS, transform to point
            const r = getPoint(props.paintRadius * 16, 0).x - getPoint(0, 0).x;
            const strokeColor = props.isErasing ? "#A00" : "#000";
            cursorCircle = (
                <g>
                    <circle cx={cx} cy={cy} r={r} stroke={strokeColor} strokeWidth="1" fill="none"/>
                </g>
            );
        }
        catch (e) {
            // ignore?
            console.log("FAILED", e);
        }
        
    }

    // background image location
    let backgroundImage = null;
    if ( Nodes.backgroundImageSrc !== undefined ) {
        const backgroundOrigin = getPoint(props.backgroundImageOriginX, props.backgroundImageOriginY);
        const backgroundEnd = getPoint(props.backgroundImageEndX, props.backgroundImageEndY);
        const backgroundWidth = backgroundEnd.x - backgroundOrigin.x
        const backgroundHeight = backgroundEnd.y - backgroundOrigin.y
        backgroundImage = (
            <image href={props.backgroundImageSrc} width={backgroundWidth} height={backgroundHeight} x={backgroundOrigin.x} y={backgroundOrigin.y} preserveAspectRatio="none"/>
        );
    }

    return (
        <svg
            id="nodes-world"
            className="leaflet-zoom-animated"
            width={props.width}
            height={props.height}
            viewBox={props.viewbox}
            style={{transform: props.transform}}
            onMouseDown={props.handleMouseDown}
            onMouseUp={props.handleMouseUp}
            onContextMenu={(e) => e.preventDefault()}
        >
            {backgroundImage}

            <defs>
                {props.svgPatterns}
            </defs>

            {props.territoryElements}
            
            {props.portElements}
            
            {cursorCircle}

        </svg>
    );
}

