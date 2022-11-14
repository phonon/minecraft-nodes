/**
 * Nodes side panel world viewer/editor
 */

"use strict";

import { useState, useEffect, useRef } from "react";

import "ui/css/nodes-scrollbar.css";
import "editor/css/editor.css";
import {GenerationPane} from "editor/panes/generation-pane.jsx";
import {NodesPane} from "editor/panes/nodes-pane.jsx";
import {OptionsPane} from "editor/panes/options-pane.jsx";
import {TerritoryPane} from "editor/panes/territory-pane.jsx";
import {WorldPane} from "editor/panes/world-pane.jsx";

import IconLoad from "assets/icon/icon-load.svg";
import IconMerge from "assets/icon/icon-merge.svg";
import IconSave from "assets/icon/icon-save.svg";
import IconTabWorld from "assets/icon/icon-tab-world.svg";
import IconTabTerritory from "assets/icon/icon-tab-territory.svg";
import IconTabNodes from "assets/icon/icon-tab-nodes.svg";
import IconTabGeneration from "assets/icon/icon-tab-generation.svg";
import IconTabOptions from "assets/icon/icon-tab-options.svg";

const PANEL_WORLD = 0;
const PANEL_TERRITORY = 1;
const PANEL_NODES = 2;
const PANEL_GENERATION = 3;
const PANEL_OPTIONS = 4;

const EditorTab = (props) => {
    let className = "nodes-editor-tab";
    if ( props.selected ) {
        className += " nodes-editor-tab-selected"
    }
    return (
        <div className={className} onClick={props.onClick}>
            <img src={props.icon} className="nodes-editor-tab-icon" draggable="false"/>
        </div>
    );
}

const EditorPane = (props) => {
    let className = "nodes-editor-pane";
    return (
        <>
        { props.active ? 
        <div id={props.id} className={className}>
            {props.children}
        </div>
        : (null)
        }
        </>
    );
}

/**
 * Editor header buttons for save/load
 * Shows tooltip underneath
 */
function EditorHeaderButton(props) {
	return (
		<div className="nodes-editor-header-btn-container">
			<div className="nodes-editor-header-btn"
				style={props.style}
				onMouseDown={props.onMouseDown}>
				<img src={props.icon} className="nodes-editor-header-btn-icon" draggable="false" style={props.styleIcon}/>
			</div>
			<div className="nodes-editor-header-btn-tooltip">
				<div className="nodes-editor-header-btn-tooltip-text">
				{props.hint}
				</div>
			</div>
		</div>
	);
}

export const Editor = (props) => {

    const [currentTab, setCurrentTab] = useState(PANEL_WORLD);
    
    useEffect(() => {
        // if editor pane is nodes editor, enable territory resource nodes editing
        if ( currentTab === PANEL_NODES ) {
            props.setEditingTerritoryNodes(true);
        }
        else {
            props.setEditingTerritoryNodes(false);
        }
    }, [currentTab]);

    // handlers for uploading file
    const fileUploader = useRef(null);     // hidden file input
    const fileUploadMerge = useRef(false); // merge uploaded file setting

    const loadFile = (merge) => {
        fileUploadMerge.current = merge;

        if ( fileUploader.current !== null ) {
            fileUploader.current.click();
        }
    };

    const handleFile = (e) => {
        let file = e.target.files[0];
        if ( file !== undefined && file.type === "application/json" ) {
            // file reader to parse json
            let reader = new FileReader();
            reader.onload = (e) => {
                let json = JSON.parse(e.target.result);
                props.load(json, fileUploadMerge.current);
            };
            reader.readAsText(file);
        }

        // clear value so onChange can detect same file
        e.target.value = null;
    };

    return (
        <div id="nodes-editor">
            <input ref={fileUploader} id="nodes-file-uploader" type="file" name="file" onChange={(e) => {handleFile(e)}}/>
            
            <div id="nodes-header">
                <div id="nodes-header-text">nodes!!! :DDD</div>
                <div id="nodes-header-btns">
                    { props.uploadEnabled ?
                    <>
                    <EditorHeaderButton hint={"Merge"} icon={IconMerge} onMouseDown={() => {loadFile(true)}}/>
                    <EditorHeaderButton hint={"Load"} icon={IconLoad} onMouseDown={() => {loadFile(false)}}/>
                    </>
                    : (null)
                    }
                    { props.editorEnabled ?
                    <EditorHeaderButton hint={"Save"} icon={IconSave} onMouseDown={props.save}/>
                     : (null)
                    }
                </div>
            </div>

            { props.editorEnabled ?
            <div id="nodes-editor-tabs-container">
                <EditorTab
                    name={"World"}
                    icon={IconTabWorld}
                    selected={currentTab === PANEL_WORLD}
                    onClick={() => setCurrentTab(PANEL_WORLD)}
                />
                { props.editorEnabled ?
                <>
                <EditorTab
                    name={"Territory"}
                    icon={IconTabTerritory}
                    selected={currentTab === PANEL_TERRITORY}
                    onClick={() => setCurrentTab(PANEL_TERRITORY)}
                />
                <EditorTab
                    name={"Nodes"}
                    icon={IconTabNodes}
                    selected={currentTab === PANEL_NODES}
                    onClick={() => setCurrentTab(PANEL_NODES)}
                />
                <EditorTab
                    name={"Generation"}
                    icon={IconTabGeneration}
                    selected={currentTab === PANEL_GENERATION}
                    onClick={() => setCurrentTab(PANEL_GENERATION)}
                />
                <EditorTab
                    name={"Options"}
                    icon={IconTabOptions}
                    selected={currentTab === PANEL_OPTIONS}
                    onClick={() => setCurrentTab(PANEL_OPTIONS)}
                />
                </>
                : (null)
                }
            </div>
            : (null) }

            <div id="nodes-editor-pane-container" className="nodes-scrollbar">
                <EditorPane
                    id="nodes-editor-pane-world"
                    active={currentTab === PANEL_WORLD}>
                    <WorldPane
                        renderTerritoryIcons={props.renderTerritoryIcons}
                        setRenderTerritoryIcons={props.setRenderTerritoryIcons}
                        renderTerritoryId={props.renderTerritoryId}
                        setRenderTerritoryId={props.setRenderTerritoryId}
                        renderTerritoryCost={props.renderTerritoryCost}
                        setRenderTerritoryCost={props.setRenderTerritoryCost}
                        renderTerritoryOpaque={props.renderTerritoryOpaque}
                        setRenderTerritoryOpaque={props.setRenderTerritoryOpaque}
                        renderTerritoryNoBorders={props.renderTerritoryNoBorders}
                        setRenderTerritoryNoBorders={props.setRenderTerritoryNoBorders}
                        renderTerritoryCapitals={props.renderTerritoryCapitals}
                        setRenderTerritoryCapitals={props.setRenderTerritoryCapitals}
                        renderTownNames={props.renderTownNames}
                        setRenderTownNames={props.setRenderTownNames}
                        resourceIcons={props.resourceIcons}
                        nodes={props.nodes}
                        towns={props.towns}
                        townsNameList={props.townsNameList}
                        selectedTown={props.selectedTown}
                        selectedTownIndex={props.selectedTownIndex}
                        selectTown={props.selectTown}
                        selectedTerritory={props.selectedTerritory}
                    />
                </EditorPane>
                { props.editorEnabled ? 
                <>
                <EditorPane
                    id="nodes-editor-pane-terr"
                    active={currentTab === PANEL_TERRITORY}
                >
                    <TerritoryPane
                        x={props.x}
                        z={props.z}
                        resourceIcons={props.resourceIcons}
                        nodes={props.nodes}
                        selectedTerritory={props.selectedTerritory}
                        createTerritory={props.createTerritory}
                        deleteTerritory={props.deleteTerritory}
                        setTerritoryName={props.setTerritoryName}
                        addNodeToTerritory={props.addNodeToTerritory}
                        removeNodeFromTerritory={props.removeNodeFromTerritory}
                        setPainting={props.setPainting}
                        togglePainting={props.togglePainting}
                        paintRadius={props.paintRadius}
                    />
                </EditorPane>

                <EditorPane
                    id="nodes-editor-pane-nodes"
                    active={currentTab === PANEL_NODES}
                >
                    <NodesPane
                        resourceIcons={props.resourceIcons}
                        nodes={props.nodes}
                        nodesNameList={props.nodesNameList}
                        createNode={props.createNode}
                        deleteNode={props.deleteNode}
                        selectedNodeIndex={props.selectedNodeIndex}
                        setSelectedNode={props.setSelectedNode}
                        renameNode={props.renameNode}
                        setNodeIcon={props.setNodeIcon}
                        setNodeData={props.setNodeData}
                    />
                </EditorPane>

                <EditorPane
                    id="nodes-editor-pane-generation"
                    active={currentTab === PANEL_GENERATION}
                >
                    <GenerationPane/>
                </EditorPane>

                <EditorPane
                    id="nodes-editor-pane-options"
                    active={currentTab === PANEL_OPTIONS}
                >
                    <OptionsPane
                        territoryCost={props.territoryCost}
                        setTerritoryCost={props.setTerritoryCost}
                    />
                </EditorPane>
                </>
                : (null)
                }
            </div>
        </div>
    );
}

