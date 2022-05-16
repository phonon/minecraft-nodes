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
import "editor/css/panes/common.css";
import "editor/css/panes/generation-pane.css";

const handleBtnSubdivide = () => {
    if ( Nodes.selectedTerritory === undefined ) {
        console.log("No selected territory to subdivide");
        return;
    }
    const id = Nodes.selectedTerritory.id;

    console.log(`[terr=${id}] Subdividing into territories...`);

    Nodes._subdivideIntoRandomTerritories(
        id,
        Nodes.generatorAverageRadius,
        Nodes.generatorScaleX,
        Nodes.generatorScaleY,
        Nodes.generatorRandomSeed,
        Nodes.generatorIterationsSmoothCenters,
        Nodes.generatorIterationsSmoothCorners,
        Nodes.generatorDeleteSmallerThan,
        Nodes.generatorMergeSmallerThan,
        Nodes.generatorCopyName,
    );
}

const handleBtnResourceDistribute = () => {
    Nodes.distributeResourcesInSelectedTerritories();
}

const handleBtnResourceRemove = () => {
    Nodes._removeAllNodesFromTerritories(Nodes.selectedTerritoryIds());
}

/**
 * Set nodes resource distribution settings
 */
const editorOnChange = (text) => {
    try {
        let data = JSON.parse(text);
        Nodes.setSetting("resourceDistributeSettings", data, false, false, true);
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

export const GenerationPane = (props) => {

    // code editor
    const refEditor = useRef(null);

    const editorOnBlur = () => {
        if ( refEditor.current !== null ) {
            editorOnChange(refEditor.current.editor.getValue());
        }
    };

    const editorText = JSON.stringify(Nodes.resourceDistributeSettings, null, 3);

    // memoization: only re-render when node value is saved
    // i.e. when json is correctly parsed
    const codeEditor = useMemo(() => CodeEditor({
        refEditor: refEditor,
        editorText: editorText,
        onBlur: editorOnBlur,
    }), [editorText]);

    return (
        <>
            <div className="nodes-editor-panel-title">Random Generation:</div>

            <div className="nodes-editor-section-header">Subdivide Territory:</div>
            <div className="nodes-editor-generation-buttons">
                <UI.Button
                    className="nodes-editor-generation-text-button"
                    onClick={handleBtnSubdivide}
                >
                    Subdivide
                </UI.Button>
                <UI.Button
                    className="nodes-editor-generation-text-button"
                    onClick={Nodes.mergeSelectedTerritories}
                >
                    Merge Selected
                </UI.Button>
            </div>

            <div className="nodes-editor-setting-field">
                <div>Average radius:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.generatorAverageRadius}
                    onChange={val => Nodes.setSetting("generatorAverageRadius", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Scale X:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.generatorScaleX}
                    onChange={val => Nodes.setSetting("generatorScaleX", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Scale Y:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.generatorScaleY}
                    onChange={val => Nodes.setSetting("generatorScaleY", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Random seed:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.generatorRandomSeed}
                    onChange={val => Nodes.setSetting("generatorRandomSeed", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Iterations smooth centers:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.generatorIterationsSmoothCenters}
                    onChange={val => Nodes.setSetting("generatorIterationsSmoothCenters", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Iterations smooth corners:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.generatorIterationsSmoothCorners}
                    onChange={val => Nodes.setSetting("generatorIterationsSmoothCorners", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Delete smaller than:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.generatorDeleteSmallerThan}
                    onChange={val => Nodes.setSetting("generatorDeleteSmallerThan", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <div>Merge smaller than:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.generatorMergeSmallerThan}
                    onChange={val => Nodes.setSetting("generatorMergeSmallerThan", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-setting-field">
                <UI.Checkbox
                    checked={Nodes.generatorCopyName}
                    onChange={val => Nodes.setSetting("generatorCopyName", val, false, false, true)}
                    label={"Copy name to new territories"}
                />
            </div>

        
            <div className="nodes-editor-section-header">Distribute Resources:</div>
            <div className="nodes-editor-generation-buttons">
                <UI.Button
                    className="nodes-editor-generation-text-button"
                    onClick={handleBtnResourceDistribute}
                >
                    Distribute
                </UI.Button>
                <UI.Button
                    className="nodes-editor-generation-text-button"
                    onClick={handleBtnResourceRemove}
                >
                    Remove all
                </UI.Button>
            </div>
            <div className="nodes-editor-setting-field">
                <div>Random seed:</div>
                <UI.InputEdit
                    className={"nodes-editor-setting-input"}
                    value={Nodes.resourceDistributeRandomSeed}
                    onChange={val => Nodes.setSetting("resourceDistributeRandomSeed", val, false, false, true)}
                />
            </div>
            <div className="nodes-editor-section-header">Resource distribution settings:</div>
            <div id="nodes-resource-distribute-ace-editor-container">
                {codeEditor}
            </div>

            <div className="nodes-editor-help">
                <div>Random generation help:</div>
                <div>- Average radius is in chunks</div>
                <div>- Resource probability format: "resource": probability, e.g.</div>
                <div>- "diamond": 1,</div>
                <div>- "gold": 2</div>
            </div>
        </>
    )
}
