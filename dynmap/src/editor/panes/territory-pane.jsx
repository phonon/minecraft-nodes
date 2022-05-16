/**
 * Territory editor panel
 */

"use strict";

import { useState, useMemo } from "react";

import Nodes from "nodes.js";
import * as UI from "ui/ui.jsx";
import IconDelete from "assets/icon/icon-x.svg";
import IconDeleteNode from "assets/icon/icon-x-thin.svg";
import IconPlus from "assets/icon/icon-plus.svg";
import IconMerge from "assets/icon/icon-terr-merge.svg";
import IconPaint from "assets/icon/icon-terr-paint.svg";

import "ui/css/nodes-scrollbar.css";
import "editor/css/panes/common.css";
import "editor/css/panes/nodes-pane.css";     // re-use nodes panel css for nodes list
import "editor/css/panes/territory-pane.css";

// ===============================
// territory nodes list
// ===============================
const TerritoryNodesList = (props) => {
    const nodesDivList = [];
    if ( props.selectedTerritory !== undefined ) {
        props.selectedTerritory.nodes.forEach( nodeName => {
            if ( props.nodes.has(nodeName) ) {
                let icon = props.nodes.get(nodeName).icon;
                let iconSrc = props.resourceIcons.get(icon);

                nodesDivList.push(
                    <div key={name} className="nodes-editor-terr-nodes-list-item">
                        <div className="nodes-editor-terr-nodes-list-item-icon">
                            {iconSrc !== undefined ?
                            <img
                                className="nodes-editor-terr-nodes-list-item-img"
                                src={iconSrc}
                                draggable={false}
                            />
                            : (null)}
                        </div>
                        <div className="nodes-editor-terr-nodes-list-name">
                            {nodeName}
                        </div>
                        <div
                            className="nodes-editor-terr-nodes-list-item-delete"
                            onClick={() => props.removeNodeFromTerritory(props.selectedTerritory.id, nodeName)}
                        >
                            <img
                                className="nodes-editor-terr-nodes-list-item-x"
                                src={IconDeleteNode}
                                draggable={false}
                            />
                        </div>
                    </div>
                );
            }
        });
    }

    return (
        <UI.List
            id="nodes-editor-terr-list"
            list={props.selectedTerritoryNodes}
            selected={undefined}
            select={undefined}
            deselect={undefined}
            heightOfItem={20}
        >
            {nodesDivList}
        </UI.List>
    );
};

export const TerritoryPane = (props) => {

    const [inputNodeName, setInputNodeName] = useState("");
    
    const selectedTerritory = props.selectedTerritory;

    // button onclick handler for adding node to selected territory
    const handleAddNodeToTerritory = () => {
        if ( selectedTerritory !== undefined ) {
            let status = props.addNodeToTerritory(selectedTerritory.id, inputNodeName);
            if ( status === true ) { // success
                setInputNodeName("");
            }
        }
    };

    // territory info
    const selectedTerritoryName = selectedTerritory !== undefined ? selectedTerritory.name : "";
    const selectedTerritoryId = `ID: ${selectedTerritory !== undefined ? selectedTerritory.id : ""}`;
    const selectedTerritoryCore = `Core: ${selectedTerritory !== undefined && selectedTerritory.coreChunk ? `${selectedTerritory.coreChunk.x},${selectedTerritory.coreChunk.y}` : ""}`
    const selectedTerritorySize = `Chunks: ${selectedTerritory !== undefined ? selectedTerritory.size : ""}`;
    const selectedTerritoryCost = `Cost: ${selectedTerritory !== undefined ? selectedTerritory.cost : ""}`;
    const selectedTerritoryNodes = selectedTerritory !== undefined ? selectedTerritory.nodes : undefined;
    const selectedTerritoryNodesCount = selectedTerritoryNodes !== undefined ? selectedTerritoryNodes.length : 0;

    // nodes selection list
    const territoryNodesList = useMemo(() => TerritoryNodesList({
        nodes: props.nodes,
        resourceIcons: props.resourceIcons,
        selectedTerritory: selectedTerritory,
        selectedTerritoryNodes: selectedTerritoryNodes,
        removeNodeFromTerritory: props.removeNodeFromTerritory,
    }), [selectedTerritory, selectedTerritoryNodesCount]);

    return (
        <>
        <div id="nodes-editor-terr-header">Territories:</div>

        <div id="nodes-editor-terr-chunk">
            <div id="nodes-editor-terr-chunk-label">Chunk:</div>
            <div>x: {props.x}</div>
            <div>z: {props.z}</div>
        </div>

        <div id="nodes-editor-terr-toolbar">
            <div id="nodes-editor-terr-toolbar-g1">
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={props.createTerritory}
                    icon={IconPlus}
                    tooltip={"Create territory"}
                />
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={() => props.deleteTerritory(Nodes.selectedTerritories.keys())}
                    icon={IconDelete}
                    tooltip={"Delete territory"}
                />
            </div>
            <div id="nodes-editor-terr-toolbar-g2">
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={props.togglePainting}
                    icon={IconPaint}
                    tooltip={"Paint territory chunks"}
                />
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={Nodes.mergeSelectedTerritories}
                    icon={IconMerge}
                    tooltip={"Merge territories"}
                />
            </div>
        </div>
        <div id="nodes-editor-brush-size">
            {`Brush Radius: ${props.paintRadius.toFixed(2)}`}
        </div>

        <div id="nodes-editor-terr-selected-header">Selected Territory:</div>
        <div id="nodes-editor-terr-selected-name">
            <div>Name:</div>
            <UI.InputEdit
                id="nodes-editor-terr-selected-name-edit"
                value={selectedTerritoryName}
                onChange={(newName) => props.setTerritoryName(selectedTerritory, newName)}
            />
        </div>
        <div>{selectedTerritoryId}</div>
        <div>{selectedTerritoryCore}</div>
        <div>{selectedTerritorySize}</div>
        <div>{selectedTerritoryCost}</div>
        <div>Nodes:</div>
        {territoryNodesList}
        <div id="nodes-editor-terr-add-node">
            <UI.Button
                className="nodes-editor-terr-tool-btn"
                onClick={handleAddNodeToTerritory}
                icon={IconPlus}
                tooltip={"Add resource node"}
            />
            <UI.InputEdit
                className="nodes-editor-terr-add-node-input"
                value={inputNodeName}
                bubbleChange={true}
                onChange={setInputNodeName}
                onEnterKey={handleAddNodeToTerritory}
            />
        </div>
        
        <div className="nodes-editor-help">
            <div>Help/Controls:</div>
            <div>- [Right click]: Select territories (when paint mode is off)</div>
            <div>- [Space bar]: Turn on/off paint mode</div>
            <div>- [Right mouse drag]: Paint chunks</div>
            <div>- [Ctrl + right mouse drag]: Erase chunks</div>
            <div>- [Shift + mouse drag]: Change brush size</div>
            <div>- [A]: While painting will create a new territory</div>
        </div>
        </>
    );

};

