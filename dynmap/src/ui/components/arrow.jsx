/**
 * arrow.jsx
 * --------------------------------------------------
 * Basic CSS arrows
 *
 * All arrows require props:
 * 	props.size = #    // size of arrow
 * 	props.color = ___ // color
 */

"use strict";

import "ui/css/components/arrow.css";

/**
 * Helper to get caret arrow style from props.
 * Same styling used for all caret arrows.
 */
const getArrowCaretStyle = function(props) {
	let size = `${props.size || 2}px`;
	let style = {
		width: size,
		height: size,
		borderStyle: "solid",
		borderColor: props.color !== undefined ? props.color : "#fff",
		borderWidth: `0 ${size} ${size} 0`,
		padding: size,
		opacity: props.opacity,
	};

	// if transition times specified
	if ( props.transitionColor !== undefined ) {
		style.transition = `border-color ${props.transitionColor}s linear`;
	}
	else if ( props.transitionOpacity !== undefined ) {
		style.transition = `opacity ${props.transitionOpacity}s ease-out`;
	}

	return style;
}

/**
 * Default caret arrows (shaped like ^, > or <)
 */
export const ArrowLeft = (props) => {
	const style = getArrowCaretStyle(props);

	return (
		<div className="nodes-arrow-left" style={style}/>
	);
};

export const ArrowRight = (props) => {
	const style = getArrowCaretStyle(props);

	return (
		<div className="nodes-arrow-right" style={style}/>
	);
};

export const ArrowUp = (props) => {
	const style = getArrowCaretStyle(props);

	return (
		<div className="nodes-arrow-up" style={style}/>
	);
};

export const ArrowDown = (props) => {
	const style = getArrowCaretStyle(props);

	return (
		<div className="nodes-arrow-down" style={style}/>
	);
};

export const ArrowNorthWest = (props) => {
	const style = getArrowCaretStyle(props);

	return (
		<div className="nodes-arrow-northwest" style={style}/>
	);
};

export const ArrowNorthEast = (props) => {
	const style = getArrowCaretStyle(props);

	return (
		<div className="nodes-arrow-northeast" style={style}/>
	);
};

export const ArrowSouthWest = (props) => {
	const style = getArrowCaretStyle(props);

	return (
		<div className="nodes-arrow-southwest" style={style}/>
	);
};

export const ArrowSouthEast = (props) => {
	const style = getArrowCaretStyle(props);

	return (
		<div className="nodes-arrow-southeast" style={style}/>
	);
};
