/**
 * color-picker.jsx
 * --------------------------------------------------
 * Hex/RGB color picker based on Sketch component from:
 * https://github.com/casesandberg/react-color
 *
 * Maintains colors in following format:
 * R: [0, 255],    // red
 * G: [0, 255],    // green
 * B: [0, 255],    // blue
 * H: [0, 360],    // hue
 * S: [0, 1],      // saturation
 * V: [0, 1],      // value ("brightness")
 */

import { useState, useEffect, useRef } from "react";
import InputEdit from "./input-edit.jsx";
import "ui/css/components/color-picker.css";

// escape key
const KEY_ESC = 27;

/**
 * Returns a hex string from RGB input
 */
function rgbToHex(r, g, b) {
    return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
}

/**
 * convert rgb input to hsv, run callback with all values
 * r0, g0, b0 are DEFAULT values for r, g, b if input is undefined
 */
function rgbToHsv(red, green, blue, callback) {
    let r = red ?? 0;
    let g = green ?? 0;
    let b = blue ?? 0;

    if ( red !== undefined ) {
        r = Math.round(Math.min(Math.max(red, 0), 255));
        r = Number.isNaN(r) ? 0 : r;
    }
    if ( green !== undefined ) {
        g = Math.round(Math.min(Math.max(green, 0), 255));
        g = Number.isNaN(g) ? 0 : g;
    }
    if ( blue !== undefined ) {
        b = Math.round(Math.min(Math.max(blue, 0), 255));
        b = Number.isNaN(b) ? 0 : b;
    }

    let hex = rgbToHex(r, g, b);

    let max = Math.max(r,g,b);
    let min = Math.min(r,g,b);
    let c = max - min; // chroma
    let s = (max === 0) ? 0 : (c / max); // sat
    let v = max / 255.0;

    // get hue
    let h;
    if ( c !== 0 ) {
        if ( max === r ) {
            h = 60.0 * ( (g < b ? 6 : 0) + (g - b) / c );
        }
        else if ( max === g ) {
            h = 60.0 * (2.0 + (b - r) / c);
        }
        else if ( max === b ) {
            h = 60.0 * (4.0 + (r - g) / c);
        }
    } else {
        h = 0;
    }

    callback(hex, r, g, b, h, s, v);
}

/**
 * convert hsv input to rgb, run callback with all values 
 * See: https://en.wikipedia.org/wiki/HSL_and_HSV#Converting_to_RGB
 */
function hsvToRgb(h, s, v, callback) {
    let hueScaled = h / 60.0;
    let c = s * v;
    let x = c * (1 - Math.abs( (hueScaled % 2) - 1));
    let m = v - c;

    let r, g, b;
    switch (Math.floor(hueScaled % 6)) {
        case 0: r = c + m, g = x + m, b = m;     break;
        case 1: r = x + m, g = c + m, b = m;     break;
        case 2: r = m,     g = c + m, b = x + m; break;
        case 3: r = m,     g = x + m, b = c + m; break;
        case 4: r = x + m, g = m,     b = c + m; break;
        case 5: r = c + m, g = m,     b = x + m; break;
        default: r = m, g = m, b = m; break;
    }

    // convert to [0, 255]
    r = Math.round(r * 255);
    g = Math.round(g * 255);
    b = Math.round(b * 255);
    const hex = rgbToHex(r, g, b);

    callback(hex, r, g, b, h, s, v);
}

/**
 * Handles change in hex string input
 * See: https://stackoverflow.com/questions/5623838/rgb-to-hex-and-hex-to-rgb
 */
function handleChangeHex(hex, callback) {
    const val = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    if ( val !== undefined ) {
        const r = parseInt(val[1], 16);
        const g = parseInt(val[2], 16);
        const b = parseInt(val[3], 16);

        // update HSV values
        rgbToHsv(r, g, b, callback);
    }
}


/**
 * Saturation and Value selection gradient
 * (S and V parts of HSV coloring)
 * horizontal: adjust saturation
 * vertical: adjust value (brightness)
 */
const Saturation = ({
    hue,
    sat,
    val,
    onChange,
}) => {

    const selectSaturationElement = useRef(null);

    const handleChange = (e) => {
        const x = e.clientX;
        const y = e.clientY;
        const rect = selectSaturationElement.current.getBoundingClientRect();

        // calculate percentX (horizontal = saturation)
        let percentX;
        if ( x > rect.right ) {
            percentX = 1;
        }
        else if ( x < rect.left ) {
            percentX = 0;
        }
        else {
            percentX = (x - rect.left) / rect.width;
        }

        // calculate percentY (vertical = lightness)
        let percentY;
        if ( y < rect.top ) {
            percentY = 1;
        }
        else if ( y > rect.bottom ) {
            percentY = 0;
        }
        else {
            percentY = 1.0 - ((y - rect.top) / rect.height);
        }

        onChange(hue, percentX, percentY)
    };

    const handleMouseDown = (e) => {
        e.preventDefault();
        handleChange(e);
        window.addEventListener("mousemove", handleChange);
        window.addEventListener("mouseup", handleMouseUp);
    };

    const handleMouseUp = () => {
        window.removeEventListener("mousemove", handleChange);
        window.removeEventListener("mouseup", handleMouseUp);
    };

    // pointer top/left position
    const pointerStyle = {
        left: (sat * 100).toString() + "%",
        top: ((1-val) * 100).toString() + "%",
    };

    // background color
    const color = {"background": `hsl(${hue},100%, 50%)`};

    return (
        <div
            className="nodes-colorpicker-saturation"
            ref={selectSaturationElement}
            style={color}
            onMouseDown={handleMouseDown}
        >
            <div className="nodes-colorpicker-saturation-white">
                <div className="nodes-colorpicker-saturation-black">
                    <div className="nodes-colorpicker-saturation-pointer"
                         style={pointerStyle}>
                        <div className="nodes-colorpicker-saturation-pointer-circle" />
                    </div>
                </div>
            </div>
        </div>
    );
};

/**
 * Hue slider component
 */
const Hue = ({
    hue,
    onChange,
}) => {
    
    const selectHueElement = useRef(null);

    const handleChange = (e) => {
        const x = e.clientX;
        const rect = selectHueElement.current.getBoundingClientRect();

        // calculate percent on the hue bar
        let percent;
        if ( x > rect.right) {
            percent = 1;
        }
        else if (x < rect.left) {
            percent = 0;
        }
        else {
            percent = (x - rect.left) / rect.width;
        }

        // handle hue change
        onChange(Math.round(percent * 360));
    };

    const handleMouseDown = (e) => {
        e.preventDefault();
        handleChange(e);
        window.addEventListener("mousemove", handleChange);
        window.addEventListener("mouseup", handleMouseUp);
    };

    const handleMouseUp = () => {
        window.removeEventListener("mousemove", handleChange);
        window.removeEventListener("mouseup", handleMouseUp);
    };

    const sliderPos = {left: (hue / 3.6).toString() + "%"};

    return (
        <div
            className="nodes-colorpicker-hue"
            ref={selectHueElement}
            onMouseDown={handleMouseDown}
        >
            <div style={sliderPos} className="nodes-colorpicker-hue-pointer">
                <div className="nodes-colorpicker-hue-pointer-slider" />
            </div>
        </div>
    );
};

/**
 * Color picker dialog component
 */
const ColorPicker = ({
    enabled,
    color,
    top,
    left,
    onChange,
    onExit,
}) => {
    const [colorHex, setColorHex] = useState("#FFFFFF");
    const [colorR, setColorR] = useState(255);
    const [colorG, setColorG] = useState(255);
    const [colorB, setColorB] = useState(255);
    const [colorH, setColorH] = useState(0);
    const [colorS, setColorS] = useState(0);
    const [colorV, setColorV] = useState(1);
    const [stylePosition, setStylePosition] = useState({display: "none"}); // hides initial render

    const refColorR = useRef(colorR);
    const refColorG = useRef(colorG);
    const refColorB = useRef(colorB);

    /**
     * Handler when any color value changes, do internal
     * color state change and call props onChange callback.
     */
    const handleChange = (hex, r, g, b, h, s, v, doOnChange = true) => {
        // set state
        setColorHex(hex);
        setColorR(r);
        setColorG(g);
        setColorB(b);
        setColorH(h);
        setColorS(s);
        setColorV(v);

        // set ref state
        refColorR.current = r;
        refColorG.current = g;
        refColorB.current = b;

        if ( onChange !== undefined && doOnChange ) {
            // pass r,g,b,hex to callback
            onChange({
                r: r,
                g: g,
                b: b,
                hex: hex,
            });
        }
    };

    /**
     * On exit handler, use refs for color values so that the
     * actual color value is not captured in useEffect.
     */
    const handleOnExit = () => {
        const r = refColorR.current;
        const g = refColorG.current;
        const b = refColorB.current;
        onExit({
            r: r,
            g: g,
            b: b,
            hex: rgbToHex(r, g, b),
        })
    };

    useEffect(() => {
        
        // calculate position for box
        const style = {};
        const useTop = (top + 190) < window.innerHeight;
        const useLeft = (left + 190) < window.innerWidth;

        // top
        if ( useTop ) {
            style.top = Math.max(0, top); // clamp to 0 as min
        }
        else {
            style.bottom = 0;
        }

        // left
        if ( useLeft ) {
            style.left = Math.max(0, left); // clamp to 0 as min
        }
        else {
            style.right = 0;
        }

        setStylePosition(style);

        // set initial color from input color type
        if ( color !== undefined ) {
            if ( typeof color === "string" ) { // assume hex
                handleChangeHex(color, (hex, r, g, b, h, s, v) => handleChange(hex, r, g, b, h, s, v, false));
            } else if ( Array.isArray(color) ) {
                rgbToHsv(color[0], color[1], color[2], (hex, r, g, b, h, s, v) => handleChange(hex, r, g, b, h, s, v, false));
            }
        }

        const onMouseDown = (e) => {
            const selected = e.target.closest("div.nodes-colorpicker");
            if ( selected === null ) {
                handleOnExit();
            }
        }

        const onEscKey = (e) => {
            if ( e.keyCode === KEY_ESC ) {
                handleOnExit();
            }
        }

        window.addEventListener("mousedown", onMouseDown);
        window.addEventListener("keydown", onEscKey);

        return () => {
            window.removeEventListener("mousedown", onMouseDown);
            window.removeEventListener("keydown", onEscKey);
        };

    }, [top, left]);


    return (
        <div
            className="nodes-colorpicker"
            style={stylePosition}
        >
            <Saturation
                hue={colorH}
                sat={colorS}
                val={colorV}
                onChange={(h, s, v) => hsvToRgb(h, s, v, handleChange)}
            />
            <Hue
                hue={colorH}
                onChange={(h) => hsvToRgb(h, colorS, colorV, handleChange)}
            />
            <div className="nodes-colorpicker-fields">
                <div className="nodes-colorpicker-fields-hex">
                    <InputEdit value={colorHex} onChange={(hex) => handleChangeHex(hex, handleChange)}/>
                </div>
                <div className="nodes-colorpicker-fields-tone">
                    <InputEdit value={colorR} onChange={(r) => rgbToHsv(r, colorG, colorB, handleChange)}/>
                </div>
                <div className="nodes-colorpicker-fields-tone">
                    <InputEdit value={colorG} onChange={(g) => rgbToHsv(colorR, g, colorB, handleChange)}/>
                </div>
                <div className="nodes-colorpicker-fields-tone">
                    <InputEdit value={colorB} onChange={(b) => rgbToHsv(colorR, colorG, b, handleChange)}/>
                </div>
            </div>
            <div className="nodes-colorpicker-fields nodes-colorpicker-fields-labels">
                <div className="nodes-colorpicker-fields-hex">
                    Hex
                </div>
                <div className="nodes-colorpicker-fields-tone">
                    R
                </div>
                <div className="nodes-colorpicker-fields-tone">
                    G
                </div>
                <div className="nodes-colorpicker-fields-tone">
                    B
                </div>
            </div>
        </div>
    );
};

export default ColorPicker;
