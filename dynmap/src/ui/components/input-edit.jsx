/**
 * editable-input.jsx
 * --------------------------------------------------
 * Editable text input (no mouse drag edit).
 * Outputs a text value. User must manually do parsing
 * and error checking for invalid outputs.
 *
 * Input props:
 *   value     (required)
 *   onChange  (required)
 *
 */

"use strict";

import { useState, useEffect, useRef } from "react";
import "ui/css/components/input-edit.css";

const InputEdit = (props) => {
    const [value, setValue] = useState(String(props.value)); // value in input

    useEffect(() => {
        setValue(String(props.value));
    }, [props.value]);

    // ref for the dom input
    const refInput = useRef(null);

    /**
     * Handle change from user manual text input into thing
     * Default: do not bubble value to props.onChange
     * because this value may/likely be invalid during editing.
     * 
     * if props.bubbleChange == true run onchange instead of
     * internal state handler
     */
    function handleChange(e) {
        if ( props.bubbleChange === true ) {
            props.onChange(e.target.value);
        }
        else {
            setValue(e.target.value);
        }
    }
    
    /**
     * Handle enter key press -> blur input
     */
    function handleKeyPress(e) {
        if ( e.key === "Enter" ) {
            if ( refInput.current !== null ) {
                if ( props.onEnterKey !== undefined ) {
                    props.onEnterKey();
                }
                else {
                    refInput.current.blur();
                }
            }
        }
    }

    /**
     * Handle blur event (defocus input)
     * --> Set value to currently held value (if valid)
     */
    function handleBlur() {
        props.onChange(value);
    }

    function focusInput() {
        if ( refInput.current !== null ) {
            refInput.current.focus();
        }
    }

    let style = {};
    style["textAlign"] = (props.textAlign !== undefined) ? props.textAlign : undefined;

    const disabled = props.disabled === true ? true : false;

    return (
        <div
            id={props.id}
            className={"nodes-input-edit " + props.className}
            onDoubleClick={focusInput}>
            <input
                ref={refInput}
                value={value}
                style={style}
                className="nodes-input-edit-input"
                onKeyDown={handleKeyPress}
                onChange={handleChange}
                onBlur={handleBlur}
                readOnly={disabled}/>
        </div>
    );
}

export default InputEdit;