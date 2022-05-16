/**
 * SVG render of a single port element
 * Rendering performed externally by nodes for memoization
 * -> WorldRenderer recieves list of port elements
 */

"use strict";

import { useRef } from "react";
import AnchorIcon from "assets/ports/anchor.png";
import "world/css/port.css";

// port svg chunk
export const Port = (props) => {
    let getPoint = props.getPoint;

    // coordinate
    const center = getPoint(props.x, props.z);

    // get icon size and apply size constraint 
    const iconSizeFromMapScale = getPoint(16, 0).x - getPoint(0, 0).x;
    // const iconSize = Math.max(Math.min(1.25 * iconSizeFromMapScale, ICON_SIZE_MAX), ICON_SIZE_MIN);
    // manually set iconSize
    let iconSize = 20;
    if ( iconSizeFromMapScale === 4 ) {
        iconSize = 20;
    }
    else if ( iconSizeFromMapScale === 8 ) {
        iconSize = 20;
    }
    else if ( iconSizeFromMapScale === 16 ) {
        iconSize = 32;
    }
    else if ( iconSizeFromMapScale === 32 ) {
        iconSize = 48;
    }
    else if ( iconSizeFromMapScale > 32 ) {
        iconSize = 64;
    }

    const svgRef = useRef(null);

    /**
     * Call tooltip render
     */
    const showTooltip = () => {
        if ( svgRef.current !== null ) {
            const rect = svgRef.current.getBoundingClientRect();
            props.showPortTooltip(props.port, rect.x, rect.y);
        }
    };

    const cx = center.x - iconSize/2;
    const cy = center.y - iconSize/2;

    return (
        <g ref={svgRef} onMouseEnter={showTooltip} onMouseLeave={props.removePortTooltip}>
            <image key={props.name} x={cx} y={cy} width={iconSize} height={iconSize} href={AnchorIcon}/>
        </g>
    );
}

/**
 * Port tooltip info div
 */
export const PortTooltip = (props) => {
    let getPoint = props.getPoint;
    let style = {
        "left": props.clientX + 50,
        "top": props.clientY - 30,
    };

    const port = props.port;

    return (
        <>
        { props.enable ? 
        <div
            id="port-tooltip"
            style={style}
        >
            <div><b>Port:</b> {port.name}</div>
            <div><b>Groups:</b> {port.groupsString}</div>
            <div><b>x:</b> {port.x}</div>
            <div><b>z:</b> {port.z}</div>
        </div>
        : (null)
        }
        </>
    );
}