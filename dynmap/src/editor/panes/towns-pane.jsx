/**
 * Territory editor panel
 */

"use strict";

import { useState, useMemo } from "react";

import {
    TownSortKey, RESIDENT_RANK_NONE, RESIDENT_RANK_OFFICER, RESIDENT_RANK_LEADER,
} from "constants.js";
import Nodes from "nodes.js";
import * as UI from "ui/ui.jsx";

import IconDelete from "assets/icon/icon-x.svg";
import IconDeleteThin from "assets/icon/icon-x-thin.svg";
import IconDeleteAll from "assets/icon/icon-terr-remove-all-owner.svg";
import IconRemoveCapture from "assets/icon/icon-terr-remove-capture.svg";
import IconPlus from "assets/icon/icon-plus.svg";
import IconTerritoryCapture from "assets/icon/icon-terr-capture.svg";
import IconPlayerLeader from "assets/icon/icon-player-leader.svg";
import IconPlayerOfficer from "assets/icon/icon-player-officer.svg";
import IconSave from "assets/icon/icon-save.svg";
import IconSortByAlphabetical from "assets/icon/icon-sort-by-alphabetical.svg";
import IconSortByPlayers from "assets/icon/icon-sort-by-players.svg";
import IconSortByTerritories from "assets/icon/icon-sort-by-territories.svg";

import "ui/css/nodes-scrollbar.css";
import "editor/css/panes/common.css";
import "editor/css/panes/towns-pane.css";     // re-use nodes panel css for nodes list
import "editor/css/panes/territory-pane.css";

const ColorSelector = ({
    color, // array of rgb [r, g, b]
}) => {
    let colorRgbStr = undefined;
    if ( color !== undefined ) {
        colorRgbStr = `rgb(${color[0]}, ${color[1]}, ${color[2]})`
    }

    const colorStyle = {
        backgroundColor: colorRgbStr,
    };

    return (
        <div className="nodes-editor-town-list-color" style={colorStyle}/>
    );
};

/**
 * List of towns.
 */
const TownsList = ({
    towns,
    selectedTownIndex,
    selectTown,
    deleteTown,
}) => {
    const townListElements = [];
    towns.forEach( town => {
        // get color
        let colorArray = town.color; // [r, g, b] format
        let color = undefined;
        if ( colorArray !== undefined ) {
            color = `rgb(${colorArray[0]}, ${colorArray[1]}, ${colorArray[2]})`
        }

        const colorStyle = {
            backgroundColor: color
        };
        
        let nationName = "";
        if ( town.nation !== undefined ) {
            nationName = `[${town.nation}]`
        }
        
        townListElements.push(
            <div key={town.name} className="nodes-editor-terr-nodes-list-item">
                <div className="nodes-editor-town-list-color" style={colorStyle}/>
                <div className="nodes-editor-terr-nodes-list-name">
                    {nationName}  {town.name}
                </div>
                <div
                    className="nodes-editor-terr-nodes-list-item-delete"
                    onClick={() => deleteTown(town.name)}
                >
                    <img
                        className="nodes-editor-terr-nodes-list-item-x"
                        src={IconDeleteThin}
                        draggable={false}
                    />
                </div>
            </div>
        );
    });

    return (
        <UI.List
            id="nodes-editor-town-list"
            list={towns}
            selected={selectedTownIndex}
            select={(town, idx) => selectTown(town, idx)}
            deselect={undefined}
            heightOfItem={20}
        >
            {townListElements}
        </UI.List>
    );
};

/**
 * List of players in each town.
 */
const TownPlayersList = ({
    players,
    selectedTown,
    removeTownResident,
}) => {
    const elements = [];
    if ( players !== undefined ) {
        for ( const p of players ) {
            let insignia = null;
            let name = p.name;
            // insignia
            if ( p.rank === RESIDENT_RANK_LEADER ) {
                insignia = <img
                    className="nodes-editor-town-player-item-rank"
                    src={IconPlayerLeader}
                    draggable={false}
                    title={"Leader"}
                />
            }
            else if ( p.rank === RESIDENT_RANK_OFFICER ) {
                insignia = <img
                    className="nodes-editor-town-player-item-rank"
                    src={IconPlayerOfficer}
                    draggable={false}
                    title={"Officer"}
                />
            }
            elements.push(
                <div key={p.name} className="nodes-editor-terr-nodes-list-item">
                    {insignia}
                    <div className="nodes-editor-terr-nodes-list-name">
                        {name}
                    </div>
                    <div
                        className="nodes-editor-terr-nodes-list-item-delete"
                        onClick={() => removeTownResident(selectedTown, p.name)}
                    >
                        <img
                            className="nodes-editor-terr-nodes-list-item-x"
                            src={IconDeleteThin}
                            draggable={false}
                        />
                    </div>
                </div>
            );
        }
    }

    return (
        <UI.List
            id="nodes-editor-town-players-list"
            list={players}
            selected={null}
            select={undefined}
            deselect={undefined}
            heightOfItem={20}
        >
            {elements}
        </UI.List>
    );
};

export const TownsPane = ({
    towns,
    townsNameList,
    selectedTownIndex,
    selectedTown,
    selectTown,
    setTownSortKey,
    createTown,
    deleteTown,
    setTownName,
    setNationName,
    setTownHome,
    addTownResident,
    removeTownResident,
    addSelectedTownSelectedTerritories,
    addSelectedTownSelectedTerritoriesAsCaptured,
    removeSelectedTownSelectedTerritories,
    removeSelectedTerritoriesOwned,
    removeSelectedTerritoriesCaptured,
}) => {
    // local state
    const [inputNewPlayerName, setInputNewPlayerName] = useState("");

    // onclick handler for input ui for adding new player to town
    const handleAddPlayerToTown = () => {
        if ( selectedTown !== undefined && inputNewPlayerName !== "" ) {
            addTownResident(selectedTown, inputNewPlayerName);
            setInputNewPlayerName("");
        }
    };

    // town view list
    const townList = useMemo(() => 
        <TownsList
            towns={towns}
            townsNameList={townsNameList}
            selectedTownIndex={selectedTownIndex}
            selectedTown={selectedTown}
            selectTown={selectTown}
            deleteTown={deleteTown}
        />
    , [towns, selectedTown]);

    // selected town info
    const selectedTownTerritories = selectedTown ? `(${selectedTown.territories.length} Territories)` : "";
    const selectedTownPlayersCount = `Players: ${selectedTown ? selectedTown.residents.length : ""}`;
    const selectedTownColor = selectedTown ? selectedTown.color : [0, 0, 0];
    const selectedTownColorTown = selectedTown ? selectedTown.colorTown : [0, 0, 0];
    const selectedTownColorNation = selectedTown ? selectedTown.colorNation : [0, 0, 0];
    
    // nodes selection list
    const selectedTownPlayersList = useMemo(() =>
        <TownPlayersList
            players={selectedTown?.residents}
            selectedTown={selectedTown}
            removeTownResident={removeTownResident}
        />
    , [selectedTown?.residents]);

    return (
        <>
        <div className="nodes-editor-list-header">
            <div className="nodes-editor-list-header-text">Towns:</div>
            <div className="nodes-editor-list-header-buttons">
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => setTownSortKey(TownSortKey.ALPHABETICAL)}
                    icon={IconSortByAlphabetical}
                    tooltip={"Sort alphabetical"}
                />
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => setTownSortKey(TownSortKey.PLAYERS)}
                    icon={IconSortByPlayers}
                    tooltip={"Sort by player count"}
                />
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => setTownSortKey(TownSortKey.TERRITORIES)}
                    icon={IconSortByTerritories}
                    tooltip={"Sort by territory count"}
                />
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => createTown()}
                    icon={IconPlus}
                    tooltip={"Create new town"}
                />
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => {console.log("TODO: SAVE TOWNS")}}
                    icon={IconSave}
                    tooltip={"Download towns.json"}
                />
            </div>
        </div>

        {townList}

        {/* Town name edit */}
        <div className="nodes-editor-setting-field">
            <div>Town:</div>
            <UI.InputEdit
                className={"nodes-editor-setting-input"}
                value={selectedTown ? selectedTown.name : ""}
                onChange={(val) => setTownName(selectedTown, val)}
            />
            <ColorSelector color={selectedTownColorTown}/>
        </div>

        {/* Nation name edit: for now, this will edit ALL nation naes */}
        <div className="nodes-editor-setting-field">
            <div>Nation:</div>
            <UI.InputEdit
                className={"nodes-editor-setting-input"}
                value={selectedTown?.nation ? selectedTown.nation : ""}
                onChange={(val) => setNationName(selectedTown?.nation, val)}
            />
            <ColorSelector color={selectedTownColorNation}/>
        </div>

        {/* Town home territory id edit */}
        <div className="nodes-editor-setting-field">
            <div>Home Terr ID:</div>
            <UI.InputEdit
                className={"nodes-editor-setting-input"}
                value={selectedTown ? selectedTown.home : ""}
                onChange={(val) => setTownHome(selectedTown, val)}
            />
        </div>

        {/* Town territory operations */}
        <div className="nodes-editor-towns-header">{`Territory operations ${selectedTownTerritories}`}</div>
        
        <div id="nodes-editor-terr-toolbar">
            <div id="nodes-editor-terr-toolbar-g1">
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={addSelectedTownSelectedTerritoriesAsCaptured}
                    icon={IconTerritoryCapture}
                    tooltip={"Capture selected for town"}
                />
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={addSelectedTownSelectedTerritories}
                    icon={IconPlus}
                    tooltip={"Add selected to town"}
                />
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={removeSelectedTownSelectedTerritories}
                    icon={IconDelete}
                    tooltip={"Remove selected from town"}
                />
            </div>
            <div id="nodes-editor-terr-toolbar-g2">
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={removeSelectedTerritoriesCaptured}
                    icon={IconRemoveCapture}
                    tooltip={"Uncapture selected from all towns"}
                />
                <UI.Button
                    className="nodes-editor-terr-tool-btn"
                    onClick={removeSelectedTerritoriesOwned}
                    icon={IconDeleteAll}
                    tooltip={"Remove selected from all towns"}
                />
            </div>
        </div>

        {/* Town player/resident operations */}
        <div className="nodes-editor-towns-header">{selectedTownPlayersCount}</div>
        {selectedTownPlayersList}
        <div id="nodes-editor-terr-add-node">
            <UI.Button
                className="nodes-editor-terr-tool-btn"
                onClick={handleAddPlayerToTown}
                icon={IconPlus}
                tooltip={"Add player to town"}
            />
            <UI.InputEdit
                className="nodes-editor-terr-add-node-input"
                value={inputNewPlayerName}
                bubbleChange={true}
                onChange={setInputNewPlayerName}
                onEnterKey={handleAddPlayerToTown}
            />
        </div>
        </>
    );

};

