/**
 * list.jsx
 * --------------------------------------------------
 * Reusable scrolling selection list component (like <select>).
 * List only renders a portion of total number of items to
 * reduce elements.
 * 
 * this shit is broken and should be replaced with logic based on:
 * https://github.com/bvaughn/react-window
 */

"use strict";

import { useState, useRef } from "react";
import "ui/css/components/list.css";
import "ui/css/nodes-scrollbar.css";

const defaultProps = {
    heightOfItem: 28,
    maxItemsToRender: 100,
};

const List = (props) => {
    // input config defaults
    const heightOfItem = props.heightOfItem !== undefined ? props.heightOfItem : defaultProps.heightOfItem;
    const maxItemsToRender = props.maxItemsToRender !== undefined ? props.maxItemsToRender : defaultProps.maxItemsToRender;
    
    // ref to the list
    const refList = React.createRef();

    // keeps track of scroll position in div
    const prevScrollTop = useRef(0);

    // state
    const [indexPosition, setIndexPosition] = useState(0);

    /**
     * Updates list index to be displayed, when user scrolls
     */
    function updateIndexPosition() {
        if ( refList.current !== null ) {
            // record scroll position
            prevScrollTop.current = refList.current.scrollTop;

            // check if scrolled enough to update list divs rendered
            const newIndexPosition = refList.current.scrollTop / heightOfItem;
            const diff = Math.abs(indexPosition - newIndexPosition);
            if ( diff >= maxItemsToRender / 2 ) {
                setIndexPosition(newIndexPosition);
            }
        }

    }

    /**
     * Force deselect any item selected
     */
    function cancelSelection() {
        if ( props.selected !== undefined && props.deselect ) {
            props.deselect(props.list[props.selected], props.selected);
        }
    }

    /**
     * Handles select/deselect item in list
     */
    function handleItemClick(index, item) {
        if ( props.select !== undefined ) {
            // click already selected item, deselect if deselect is defined
            if ( props.selected === index ) {
                if ( props.deselect ) {
                    props.deselect(props.list[index], index);
                }
            }
            // select a new item
            else {
                // deselect existing item
                if ( props.selected !== undefined && props.deselect ) {
                    props.deselect(props.list[props.selected], props.selected);
                }
                // select new item
                props.select(props.list[index], index);
            }
        }
    }

    const listLength = React.Children.count(props.children);
    const startIndex = (indexPosition - maxItemsToRender) > 0 ?
                            Math.floor(indexPosition) - maxItemsToRender : 0;

    const endIndex = ((indexPosition + maxItemsToRender) >= listLength) ?
                            listLength : Math.floor(indexPosition + maxItemsToRender);

    // spacers at top and bottom
    const topSpacer = startIndex * heightOfItem;
    const botSpacer = (listLength - endIndex) * heightOfItem;

    // overall list class
    const listClass = props.className !== undefined ? "nodes-basic-list nodes-scrollbar " + props.className : "nodes-basic-list nodes-scrollbar";

    // item style
    const itemStyle = {
        height: heightOfItem,
        lineHeight: String(heightOfItem) + "px",
    };
    if ( props.itemStyle !== undefined ) {
        Object.assign(itemStyle, props.itemStyle);
    }

    let list = React.Children.toArray(props.children).slice(startIndex, endIndex).map( (item, index) =>
        <div
            className={(props.selected === startIndex + index) ?
                "nodes-simple-select-list-item nodes-simple-select-list-item-selected" :
                "nodes-simple-select-list-item"}
            key={"nodes-list-item-" + index.toString()}
            onMouseDown={() => handleItemClick(startIndex + index, item)}
            style={itemStyle}
        >
            {item}
        </div>
    );

    //console.log(props.list.slice(startIndex, endIndex));
    return (
        <div
            ref={refList}
            className={listClass}
            id={props.id}
            style={props.style}
            onScroll={updateIndexPosition}
        >
            <div key="nodes-list-top-spacer"
                    style={{
                    height: topSpacer
                }}/>

            {list}

            <div key="nodes-list-bottom-spacer"
                    style={{
                    height: botSpacer
                }}/>
        </div>
    );
}

export default List;