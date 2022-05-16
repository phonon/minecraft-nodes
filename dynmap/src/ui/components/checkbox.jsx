/**
 * checkbox.jsx
 * --------------------------------------------------
 * Toggleable checkbox with text label
 *
 * Input props:
 *   checked   (required)
 *   onChange  (required)
 *
 */

"use strict";

import "ui/css/components/checkbox.css";

const Checkbox = (props) => {

	function handleChange(e) {
		props.onChange(!props.checked);
	}

	const disabled = props.disabled === true ? true : false;
	let className = "nodes-checkbox-container"
	if ( props.className !== undefined ) {
		className += " " + props.className;
	}

	return (
		<div
			id={props.id}
			className="nodes-checkbox-container"
		>
			<input
				type="checkbox"
				checked={props.checked}
				className="nodes-checkbox"
				onChange={handleChange}
				readOnly={disabled}
			/>

			<div
				className="nodes-checkbox-label"
				onMouseDown={handleChange}
			>
				{props.label}
			</div>
		</div>
	);
}

export default Checkbox;