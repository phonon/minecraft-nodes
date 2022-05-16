/**
 * Options panel
 * -----------------------------
 * Global map settings
 * 
 * Right now just has territory cost parameter
 * overrides
 */

"use strict";

import { useState, useMemo, useRef } from "react";

import Nodes from "nodes.js";
import * as UI from "ui/ui.jsx";
import AceEditor from 'react-ace';

import 'ui/css/ace-editor-nodes-dark.js';
import 'ui/css/ace-editor.css';
import "ui/css/nodes-scrollbar.css";
import "editor/css/panes/options-pane.css";     // re-use nodes panel css for nodes list

// upload background image file
const uploadImage = (e) => {
    let file = e.target.files[0];
    if ( file !== undefined && file.type.startsWith("image") ) {
        // file reader to parse json
        let reader = new FileReader();
        reader.onload = (e) => {
            Nodes.setBackgroundImage(e.target.result, file.name);
        };
        reader.readAsDataURL(file);
    }

    // clear value so onChange can detect same file
    e.target.value = null;
};

// handler for manually typing and changing background image
const handleSetBackgroundImage = (val) => {
    if ( val !== Nodes.backgroundImageName ) {
        Nodes.setBackgroundImage(val, val);
    }
}

/**
 * Nodes default resource parameter editor on change:
 * Update Nodes default resource properties object
 * (this generates editor re-render)
 */
const editorOnChange = (text) => {
    try {
        let data = JSON.parse(text);
        Nodes._setDefaultNodeProperties(data);
    }
    catch {
        // ignore
    }
};

const CodeEditor = (props) => {
    return (
        <AceEditor
            ref={props.refEditor}
            mode="javascript"
            theme="nodes_dark"
            name="nodes-code-ace-editor"
            onFocus={() => {}}
            onBlur={props.onBlur}
            onChange={editorOnChange}
            width={'100%'}
            height={'100%'}
            fontSize={13}
            tabSize={3}
            wrapEnabled={true}
            showPrintMargin={true}
            showGutter={true}
            highlightActiveLine={true}
            value={props.editorText}
            setOptions={{
                wrap: true,
                wrapBehavioursEnabled: true,
                showLineNumbers: true,
                tabSize: 3,
            }}
            editorProps={{
                $blockScrolling: Infinity
            }}
        />
    );
}

export const OptionsPane = (props) => {

    // handlers for uploading file
    const fileUploader = useRef(null); // hidden file input

    // code editor
    const refEditor = useRef(null);

    const editorOnBlur = () => {
        if ( refEditor.current !== null ) {
            editorOnChange(refEditor.current.editor.getValue());
        }
    };

    const editorText = JSON.stringify(Nodes.defaultNodeProperties, null, 3);

    // memoization: only re-render when node value is saved
    // i.e. when json is correctly parsed
    const codeEditor = useMemo(() => CodeEditor({
        refEditor: refEditor,
        editorText: editorText,
        onBlur: editorOnBlur,
    }), [editorText]);

    const handleUploadImage = () => {
        if ( fileUploader.current !== null ) {
            fileUploader.current.click();
        }
    };

    return (
        <>
            <input ref={fileUploader} id="nodes-editor-options-image-upload" type="file" name="file" onChange={(e) => {uploadImage(e)}}/>

            <div className="nodes-editor-panel-title">Options:</div>

            <div className="nodes-editor-section-header">Global Territory Costs:</div>
            <div className="nodes-editor-setting-field">
                <div>Base:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={props.territoryCost.constant}
                    onChange={(val) => runSetCost(props.setTerritoryCost, parseInt(val), props.territoryCost.scale)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Scale:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={props.territoryCost.scale}
                    onChange={(val) => runSetCost(props.setTerritoryCost, props.territoryCost.constant, parseFloat(val))}
                />
            </div>

            <div className="nodes-editor-section-header">Default resource properties:</div>
            <div id="nodes-ace-editor-default-resource-container">
                {codeEditor}
            </div>

            <div className="nodes-editor-section-header">Background Image:</div>
            <div className="nodes-editor-setting-field">
                <div>URL:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.backgroundImageName}
                    onChange={handleSetBackgroundImage}
                />
                <UI.Button
                    id="nodes-editor-options-upload-img-btn"
                    onClick={handleUploadImage}
                >
                    Upload
                </UI.Button>
            </div>
            <div className="nodes-editor-setting-field">
                <div>Origin X:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.backgroundImageOriginX}
                    onChange={(val) => Nodes.setBackgroundImageOriginX(parseInt(val))}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Origin Y:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.backgroundImageOriginY}
                    onChange={(val) => Nodes.setBackgroundImageOriginY(parseInt(val))}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Width:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.backgroundImageWidth}
                    onChange={(val) => Nodes.setBackgroundImageWidth(parseInt(val))}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Height:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.backgroundImageHeight}
                    onChange={(val) => Nodes.setBackgroundImageHeight(parseInt(val))}
                />
            </div>

            <div className="nodes-editor-help">
                <div>Background Image Help:</div>
                <div>- Type image url in text box and press [Enter]</div>
                <div>- Or upload file</div>
            </div>
        </>
    )
}

const runSetCost = (setTerritoryCost, constant, scale) => {
    // check if number valid
    if ( isNaN(constant) || isNaN(scale) ) {
        console.error("Invalid number: NaN")
        return
    }

    setTerritoryCost(constant, scale);
}