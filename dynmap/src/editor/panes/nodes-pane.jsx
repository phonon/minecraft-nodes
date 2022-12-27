/**
 * Resource library editor
 * --------------------------------
 * Allow editing of resource JSON definitions
 * 
 * TODO: should rename this to "Resource" pane.
 * term "Node" is legacy from when a resource node was
 * considered a "node". Now common definition people 
 * refer to "node" as a territory...
 * 
 * all terms "node" in here should be "resource"
 */

"use strict";

import { useState, useMemo, useRef } from "react";

import ace from "ace-builds/src-noconflict/ace";
import "ace-builds/src-min-noconflict/mode-javascript";
import "ace-builds/src-min-noconflict/ext-language_tools";
import "ace-builds/src-min-noconflict/ext-spellcheck";
import aceJavascriptWorkerUrl from "file-loader!ace-builds/src-noconflict/worker-javascript";
ace.config.setModuleUrl("ace/mode/javascript_worker", aceJavascriptWorkerUrl)
import AceEditor from "react-ace";

import "ui/css/ace-editor-nodes-dark.js";
import "ui/css/ace-editor.css";
import "ui/css/nodes-scrollbar.css";
import "editor/css/panes/common.css";
import "editor/css/panes/nodes-pane.css";

import * as UI from "ui/ui.jsx";
import IconDelete from "assets/icon/icon-x-thin.svg";
import IconPlus from "assets/icon/icon-plus.svg";

// ===============================
// nodes list panel
// ===============================
const NodesList = (props) => {
    const nodesDivList = [];
    props.nodes.forEach( (node, name) => {
        let icon = node.icon;
        let iconSrc = props.resourceIcons.get(icon);

        nodesDivList.push(
            <div key={name} className="nodes-editor-nodes-list-item">
                <div className="nodes-editor-nodes-list-item-icon">
                    {iconSrc !== undefined ?
                    <img
                        className="nodes-editor-nodes-list-item-img"
                        src={iconSrc}
                        draggable={false}
                    />
                    : (null)}
                </div>
                <UI.InputEdit
                    className="nodes-editor-nodes-list-edit-name"
                    value={name}
                    onChange={(newName) => props.renameNode(name, newName)}
                    disabled={!props.canRenameNode}
                />
                <div
                    className="nodes-editor-nodes-list-item-delete"
                    onClick={() => props.deleteNode(name)}
                >
                    <img
                        className="nodes-editor-nodes-list-item-x"
                        src={IconDelete}
                        draggable={false}
                    />
                </div>
            </div>
        );
    });

    return (
        <UI.List
            list={props.nodesNameList}
            selected={props.selectedNodeIndex}
            select={(name, idx) => props.setSelectedNode(name, idx)}
            deselect={() => {}}
            heightOfItem={24}
        >
            {nodesDivList}
        </UI.List>
    );
};

// ===============================
// icon selection panel
// ===============================
const SelectIconButton = (props) => {
    let className = "nodes-editor-nodes-icon-btn";
    if ( props.selected ) {
        className += " nodes-editor-nodes-icon-btn-selected";
    }
    return (
        <div className={className} onClick={props.onClick}>
            <img key={props.icon} className="nodes-editor-nodes-icon-img" src={props.iconSrc} title={props.icon}/>
        </div>
    );
}

const IconSelection = (props) => {
    return (
        <div id="nodes-editor-nodes-icon-selection" className="nodes-scrollbar">
            {Array.from(props.resourceIcons.keys()).map(icon => 
                <SelectIconButton
                    key={icon}
                    icon={icon}
                    iconSrc={props.resourceIcons.get(icon)}
                    selected={props.nodeIcon === icon}
                    onClick={() => props.setNodeIcon(icon)}
                />
            )}
        </div>
    );
    
}

const CodeEditor = (props) => {
    return (
        <AceEditor
            ref={props.refEditor}
            mode="javascript"
            theme="nodes_dark"
            name="nodes-code-ace-editor"
            onFocus={() => {}}
            onBlur={props.handleEditorBlur}
            onChange={props.writeNodeData}
            width={"100%"}
            height={"100%"}
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

export const NodesPane = (props) => {

    // render node editor
    const [renderNodeEditor, setRenderNodeEditor] = useState(true);

    // code editor
    const refEditor = useRef(null);

    // selected node data
    let selectedNodeIcon = "";
    let selectedNode = undefined;
    let selectedNodeName = undefined;
    let editorText = "";
    if ( props.selectedNodeIndex !== undefined ) {
        selectedNodeName = props.nodesNameList[props.selectedNodeIndex];
        selectedNode = props.nodes.get(selectedNodeName);
        selectedNodeIcon = selectedNode.icon;
        editorText = JSON.stringify(selectedNode, null, 3);
    }
    else {
        editorText = Nodes.defaultNodeString;
    }
    
    // nodes selection list
    const nodesList = useMemo(() => NodesList({
        nodes: props.nodes,
        resourceIcons: props.resourceIcons,
        nodesNameList: props.nodesNameList,
        selectedNodeIndex: props.selectedNodeIndex,
        setSelectedNode: props.setSelectedNode,
        renameNode: props.renameNode,
        deleteNode: props.deleteNode,
        canRenameNode: renderNodeEditor,
    }), [props.nodesNameList, props.selectedNodeIndex, selectedNodeIcon]);

    // nodes editor header text
    const nodesEditorHeaderText = renderNodeEditor ? "v Resource Node Properties:" : "^ Resource Node Properties:";

    // icon selection, memoize when selected icon not changed
    // -> large number of image draws VERY slow
    const iconSelection = useMemo(() => IconSelection({
        resourceIcons: props.resourceIcons,
        nodeIcon: selectedNodeIcon,
        setNodeIcon: (icon) => props.setNodeIcon(selectedNodeName, icon),
    }), [selectedNodeName, props.selectedNodeIndex, selectedNodeIcon]);

    // editor code parse and save
    const writeNodeData = (text) => {
        try {
            if ( selectedNodeName !== undefined ) {
                let data = JSON.parse(text);
                props.setNodeData(selectedNodeName, data)
            }
        }
        catch {
            // ignore
        }
    };

    const handleEditorBlur = () => {
        if ( refEditor.current !== null ) {
            writeNodeData(refEditor.current.editor.getValue());
        }
    };

    // memoization: only re-render when node value is saved
    // i.e. when json is correctly parsed
    const codeEditor = useMemo(() => CodeEditor({
        refEditor: refEditor,
        editorText: editorText,
        writeNodeData: writeNodeData,
        handleEditorBlur: handleEditorBlur,
    }), [editorText]);

    return (
        <>
        <div id="nodes-editor-nodes-header">
            <div className="nodes-editor-nodes-header-text">Node Library:</div>
            <div className="nodes-editor-nodes-header-buttons">
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => props.createNode()}
                    icon={IconPlus}
                />
            </div>
        </div>

        <div id="nodes-editor-nodes-list">
            {nodesList}
        </div>
        
        <div id="nodes-editor-nodes-editor-folder" onClick={() => setRenderNodeEditor(!renderNodeEditor)}>
            {nodesEditorHeaderText}
        </div>

        { renderNodeEditor ? 
            <>
            <div id="nodes-editor-nodes-choose-icon">
                {iconSelection}
            </div>

            <div id="nodes-code-editor-container">
                {codeEditor}
            </div>
            </>
            : (null)
        }
        

        <div className="nodes-editor-help">
            <div>Help/Controls:</div>
            <div>- [Ctrl + Right click]: Add/remove node from territory on map</div>
        </div>

        </>
    );

};

