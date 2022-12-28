/**
 * Player Town / World global viewer pane
 * 
 * Default pane for user to view towns and different territories
 */

"use strict";

import { useState, useMemo } from "react";

import {
    RESIDENT_RANK_NONE, RESIDENT_RANK_OFFICER, RESIDENT_RANK_LEADER,
    RENDER_TOWN_NAMETAG_NONE, RENDER_TOWN_NAMETAG_TOWN, RENDER_TOWN_NAMETAG_NATION,
    TownSortKey,
} from "constants.js";
import * as UI from "ui/ui.jsx";
import IconOptionIcons from "assets/icon/icon-option-icons.svg";
import IconOptionId from "assets/icon/icon-option-id.svg";
import IconOptionCost from "assets/icon/icon-option-cost.svg";
import IconOptionOpaque from "assets/icon/icon-option-opaque.svg";
import IconOptionNoBorder from "assets/icon/icon-option-noborder.svg";
import IconOptionTownName from "assets/icon/icon-option-town-name.svg";
import IconOptionNationName from "assets/icon/icon-option-nation-name.svg";
import IconOptionCapitals from "assets/icon/icon-map-capital.svg";
import IconSortByAlphabetical from "assets/icon/icon-sort-by-alphabetical.svg";
import IconSortByPlayers from "assets/icon/icon-sort-by-players.svg";
import IconSortByTerritories from "assets/icon/icon-sort-by-territories.svg";

import IconPlayerLeader from "assets/icon/icon-player-leader.svg";
import IconPlayerOfficer from "assets/icon/icon-player-officer.svg";

import "ui/css/nodes-scrollbar.css";
import "editor/css/panes/common.css";
import "editor/css/panes/world-pane.css";
import "editor/css/panes/territory-pane.css";

// ===============================
// towns list
// ===============================
const TownsList = (props) => {
    const townListElements = [];
    props.towns.forEach( town => {
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
            <div key={name} className="nodes-editor-town-list-item">
                <div className="nodes-editor-town-list-color" style={colorStyle}/>
                <div className="nodes-editor-town-list-name">
                    {nationName}  {town.name}
                </div>
            </div>
        );
    });

    return (
        <UI.List
            id="nodes-editor-town-list"
            list={props.towns}
            selected={props.selectedTownIndex}
            select={(town, idx) => props.selectTown(town, idx)}
            deselect={undefined}
            heightOfItem={20}
        >
            {townListElements}
        </UI.List>
    );
};

// ===============================
// town players list
// ===============================
const TownPlayersList = (props) => {
    const elements = [];
    if ( props.players !== undefined ) {
        for ( const p of props.players ) {
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
                <div key={p.name} className="nodes-editor-town-player-item">
                    {insignia}
                    <div>{name}</div>
                </div>
            );
        }
    }

    return (
        <UI.List
            id="nodes-editor-town-players-list"
            list={props.players}
            selected={null}
            select={undefined}
            deselect={undefined}
            heightOfItem={20}
        >
            {elements}
        </UI.List>
    );
};

// ===============================
// territory nodes list
// ===============================
const TerritoryNodesList = (props) => {
    const nodesDivList = [];
    if ( props.selectedTerritory !== undefined ) {
        props.selectedTerritory.nodes.forEach( nodeName => {
            if ( props.nodes.has(nodeName) ) {
                let resourceNode = props.nodes.get(nodeName);
                let icon = resourceNode.icon;
                let iconSrc = props.resourceIcons.get(icon);

                nodesDivList.push(
                    <div
                        key={name}
                        className="nodes-editor-terr-nodes-list-item"
                        onMouseOver={() => props.setResourceTooltip(resourceNode)}
                        onMouseLeave={() => props.setResourceTooltip(undefined)}
                    >
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
                    </div>
                );
            }
        });
    }

    return (
        <UI.List
            id="nodes-editor-world-nodes-list"
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

// render resource node info tooltip
// appears fixed-position on right side of nodes list
const ResourceTooltip = (props) => {
    const node = props.node;

    const incomeList = node.hasOwnProperty("income") ? Object.keys(node.income) : [];
    const oreList = node.hasOwnProperty("ore") ? Object.keys(node.ore) : [];
    const cropsList = node.hasOwnProperty("crops") ? Object.keys(node.crops) : [];
    const animalsList = node.hasOwnProperty("animals") ? Object.keys(node.animals) : [];
    const icon = props.resourceIcons.get(node.icon);

    return (
        <div id="nodes-editor-resource-tooltip">
            <div id="nodes-editor-resource-tooltip-header">
                <div id="nodes-editor-resource-tooltip-header-icon">
                    {icon !== undefined ?
                    <img
                        id="nodes-editor-resource-tooltip-header-img"
                        src={icon}
                        draggable={false}
                    />
                    : (null)}
                </div>
                <div id="nodes-editor-resource-tooltip-header-name">
                    {node.name}
                </div>
            </div>
            <div className="nodes-editor-resource-tooltip-property">
                Income:
                {
                    incomeList.length === 0 ?
                    <div className="nodes-editor-resource-tooltip-property-none">None</div>
                    : (null)
                }
            </div>
            {
                incomeList.map( type => 
                    <div className="nodes-editor-resource-tooltip-property-item" key={type}>
                        {`- ${type}: ${node.income[type]}`}
                    </div>
                )
            }

            <div className="nodes-editor-resource-tooltip-property">
                Ore:
                {
                    oreList.length === 0 ?
                    <div className="nodes-editor-resource-tooltip-property-none">None</div>
                    : (null)
                }
            </div>
            {
                oreList.map( type => 
                    <div className="nodes-editor-resource-tooltip-property-item" key={type}>
                        {`- ${type}: ${node.ore[type]}`}
                    </div>
                )
            }

            <div className="nodes-editor-resource-tooltip-property">
                Crops:
                {
                    cropsList.length === 0 ?
                    <div className="nodes-editor-resource-tooltip-property-none">None</div>
                    : (null)
                }
            </div>
            {
                cropsList.map( type => 
                    <div className="nodes-editor-resource-tooltip-property-item" key={type}>
                        {`- ${type}: ${node.crops[type]}`}
                    </div>
                )
            }

            <div className="nodes-editor-resource-tooltip-property">
                Animals:
                {
                    animalsList.length === 0 ?
                    <div className="nodes-editor-resource-tooltip-property-none">None</div>
                    : (null)
                }
            </div>
            {
                animalsList.map( type => 
                    <div className="nodes-editor-resource-tooltip-property-item" key={type}>
                        {`- ${type}: ${node.animals[type]}`}
                    </div>
                )
            }

        </div>
    )
};

export const WorldPane = (props) => {

    // resource node info tooltip state
    // -> if this is node object, make visible
    //    else, undefined -> invisible
    const [resourceTooltip, setResourceTooltip] = useState(undefined);

    // town view list
    const townList = useMemo(() => 
        <TownsList
            towns={props.towns}
            townsNameList={props.townsNameList}
            selectedTownIndex={props.selectedTownIndex}
            selectedTown={props.selectedTown}
            selectTown={props.selectTown}
        />
    , [props.towns, props.selectedTown]);

    // town info
    const town = props.selectedTown;
    const selectedTownName = `Town: ${town ? town.name : ""}`;
    const selectedTownNation = `Nation: ${town !== undefined && town.nation !== undefined ? town.nation : ""}`;
    const selectedTownTerritories = `Territories: ${town ? town.territories.length : ""}`;
    const selectedTownPlayersCount = `Players: ${town ? town.residents.length : ""}`;

    // nodes selection list
    const selectedTownPlayersList = useMemo(() =>
        <TownPlayersList
            players={town?.residents}
        />
    , [props.selectedTown]);

    // selected territory info
    const selectedTerritory = props.selectedTerritory;
    const selectedTerritoryName = `Name: ${selectedTerritory !== undefined ? selectedTerritory.name : ""}`;
    const selectedTerritoryId = `ID: ${selectedTerritory ? selectedTerritory.id : ""}`;
    const selectedTerritoryCore = `Core: ${selectedTerritory && selectedTerritory.coreChunk ? `${selectedTerritory.coreChunk.x},${selectedTerritory.coreChunk.y}` : ""}`
    const selectedTerritorySize = `Chunks: ${selectedTerritory ? selectedTerritory.size : ""}`;
    const selectedTerritoryCost = `Cost: ${selectedTerritory !== undefined ? selectedTerritory.cost : ""}`;
    const selectedTerritoryNodes = selectedTerritory !== undefined ? selectedTerritory.nodes : undefined;
    const selectedTerritoryNodesCount = selectedTerritoryNodes !== undefined ? selectedTerritoryNodes.length : 0;
    
    // territory nodes list
    const territoryNodesList = useMemo(() => TerritoryNodesList({
        nodes: props.nodes,
        resourceIcons: props.resourceIcons,
        selectedTerritory: selectedTerritory,
        selectedTerritoryNodes: selectedTerritoryNodes,
        setResourceTooltip: setResourceTooltip,
    }), [selectedTerritory, selectedTerritoryNodesCount]);
    
    return (
        <>
        <div id="nodes-editor-options-header">Map Options:</div>
        <div id="nodes-editor-options">
            <UI.Button
                className="nodes-editor-option-btn"
                onClick={() => props.setRenderTerritoryIcons(!props.renderTerritoryIcons)}
                icon={IconOptionIcons}
                pressed={props.renderTerritoryIcons}
                tooltip={"Show resource icons"}
            />
            <UI.Button
                className="nodes-editor-option-btn"
                onClick={() => props.setRenderTerritoryId(!props.renderTerritoryId)}
                icon={IconOptionId}
                pressed={props.renderTerritoryId}
                tooltip={"Show territory ids"}
            />
            <UI.Button
                className="nodes-editor-option-btn"
                onClick={() => props.setRenderTerritoryCost(!props.renderTerritoryCost)}
                icon={IconOptionCost}
                pressed={props.renderTerritoryCost}
                tooltip={"Show territory cost"}
            />
            <UI.Button
                className="nodes-editor-option-btn"
                onClick={() => props.setRenderTerritoryOpaque(!props.renderTerritoryOpaque)}
                icon={IconOptionOpaque}
                pressed={props.renderTerritoryOpaque}
                tooltip={"Solid nation colors"}
            />
            <UI.Button
                className="nodes-editor-option-btn"
                onClick={() => props.setRenderTerritoryNoBorders(!props.renderTerritoryNoBorders)}
                icon={IconOptionNoBorder}
                pressed={props.renderTerritoryNoBorders}
                tooltip={"No borders"}
            />
            <UI.Button
                className="nodes-editor-option-btn"
                onClick={() => props.setRenderTownNames(props.renderTownNames === RENDER_TOWN_NAMETAG_TOWN ? RENDER_TOWN_NAMETAG_NONE : RENDER_TOWN_NAMETAG_TOWN)}
                icon={IconOptionTownName}
                pressed={props.renderTownNames === RENDER_TOWN_NAMETAG_TOWN}
                tooltip={"Town names"}
            />
            <UI.Button
                className="nodes-editor-option-btn"
                onClick={() => props.setRenderTownNames(props.renderTownNames === RENDER_TOWN_NAMETAG_NATION ? RENDER_TOWN_NAMETAG_NONE : RENDER_TOWN_NAMETAG_NATION)}
                icon={IconOptionNationName}
                pressed={props.renderTownNames === RENDER_TOWN_NAMETAG_NATION}
                tooltip={"Nation names"}
            />
            <UI.Button
                className="nodes-editor-option-btn"
                onClick={() => props.setRenderTerritoryCapitals(!props.renderTerritoryCapitals)}
                icon={IconOptionCapitals}
                pressed={props.renderTerritoryCapitals}
                tooltip={"Town Capitals"}
            />
        </div>

        <div className="nodes-editor-list-header">
            <div className="nodes-editor-list-header-text">Towns:</div>
            <div className="nodes-editor-list-header-buttons">
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => props.setTownSortKey(TownSortKey.ALPHABETICAL)}
                    icon={IconSortByAlphabetical}
                    tooltip={"Sort alphabetical"}
                />
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => props.setTownSortKey(TownSortKey.PLAYERS)}
                    icon={IconSortByPlayers}
                    tooltip={"Sort by player count"}
                />
                <UI.Button
                    className="nodes-editor-nodes-header-btn"
                    onClick={() => props.setTownSortKey(TownSortKey.TERRITORIES)}
                    icon={IconSortByTerritories}
                    tooltip={"Sort by territory count"}
                />
            </div>
        </div>

        {townList}

        <div id="nodes-editor-town-info">
            <div>{selectedTownName}</div>
            <div>{selectedTownNation}</div>
            <div>{selectedTownTerritories}</div>
            <div>{selectedTownPlayersCount}</div>
        </div>
        {selectedTownPlayersList}

        <div id="nodes-editor-terr-selected-header">Selected Territory:</div>
        <div>{selectedTerritoryName}</div>
        <div id="nodes-editor-world-terr-info">
            <div id="nodes-editor-world-terr-info-left">
                <div>{selectedTerritoryId}</div>
                <div>{selectedTerritoryCore}</div>
            </div>
            <div id="nodes-editor-world-terr-info-right">
                <div>{selectedTerritorySize}</div>
                <div>{selectedTerritoryCost}</div>
            </div>
        </div>

        <div id="nodes-editor-nodes-list-container">
            <div>Nodes:</div>
            {territoryNodesList}

            { resourceTooltip !== undefined ?
                <ResourceTooltip node={resourceTooltip} resourceIcons={props.resourceIcons}/>
                : (null)
            }
        </div>

        <div className="nodes-editor-help">
            <div>Help:</div>
            <div>- Select territory with right mouse click to view</div>
            <div>- Hover over resource node in list for details</div>
        </div>
        
        </>
    );

};

