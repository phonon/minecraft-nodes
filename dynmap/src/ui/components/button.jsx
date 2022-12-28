/**
 * button.jsx
 * --------------------------------------------------
 * Basic button
 */

"use strict";

import "ui/css/components/button.css";

const Button = (props) => {
    let style = {
        backgroundColor: (props.pressed === true) ? "#333" : "#111",
    };
    let className = props.className ? "nodes-button " + props.className : "nodes-button";
    let iconClassName = "nodes-button-icon";

    if ( props.pressed === true ) {
        style.borderBottom = "0";
        style.borderLeft = "0";
        style.borderTop = "solid 1px #000";
        style.borderRight = "solid 1px #000";

        iconClassName += " nodes-button-icon-pressed";
    }

    
    return (
        <div
            id={props.id}
            className={className}
            onClick={props.onClick}
            style={style}
            title={props.tooltip}
        >
            {props.children}
            {
            props.icon !== undefined ?
                <img src={props.icon} className={iconClassName} draggable="false"/>
            : (null)}
        </div>
    );
};

export default Button;
