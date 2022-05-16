extern crate wasm_bindgen;
extern crate fnv;

use std::collections::{BTreeMap};
use std::collections::btree_map::Entry;
use std::iter::FromIterator;
use wasm_bindgen::prelude::*;
use fnv::{FnvHashMap, FnvHashSet};
use territory::territory::{Territory};
use territory::geometry::Point;
use territory::generator::{CellDiagram, generate_random_cells};

#[wasm_bindgen]
extern {
	#[wasm_bindgen(js_namespace = console)]
	fn log(msg: &str);

	#[wasm_bindgen(js_namespace = console)]
	fn error(msg: &str);
}

fn print1(s: &str) {
	// log(s);
}

fn print2(s: &str) {
	log(s);
}

// max territory node colors
const MAX_COLORS: usize = 5;

#[wasm_bindgen]
pub struct World {
    // grid maps each coord -> territory id
    grid: FnvHashMap<Point<i32>, u32>,
    // all chunks occupied by any territory, may be redundant with grid could simplify
    grid_occupied_coords: FnvHashSet<Point<i32>>,
    // world chunk to coord scale (= 16 blocks / grid chunk)
    grid_scale: i32,
    // map of territory id => Territory
    territories: FnvHashMap<u32, Territory>,
    // id counter for territories
    territory_id_counter: u32,
}

// rust internal functions
impl World {
    pub fn add_territory(&mut self, terr: Territory) {
        for p in terr.coords.iter() {
            self.grid_occupied_coords.insert(*p);
            self.grid.insert(*p, terr.id);
        }

        self.territories.insert(terr.id, terr);
    }

    pub fn remove_territory(&mut self, id: u32) -> Option<Territory> {
        // remove territory and its chunks from world grid
        if let Some(territory) = self.territories.remove(&id) {
            let chunks = &territory.coords;
            for p in chunks.iter() {
                self.grid_occupied_coords.remove(&p);
                self.grid.remove(&p);
            }

            return Some(territory);
        }

        None
    }

    pub fn add_points_to_territory<T: IntoIterator<Item = Point<i32>>>(&mut self, id: u32, coords: T) -> bool {
        // insert coords into territory and to grid, overwrites any previous territory coords
        if let Some(territory) = self.territories.get_mut(&id) {
            for p in coords.into_iter() {
                self.grid_occupied_coords.insert(p);
                self.grid.insert(p, id);
                territory.coords.insert(p);
            }
            return true;
        }

        return false;
    }

}

#[wasm_bindgen]
impl World {
    #[wasm_bindgen(constructor)]
    pub fn new(grid_scale: i32) -> Option<World> {
        Some(World {
            grid: FnvHashMap::default(),
            grid_occupied_coords: FnvHashSet::default(),
            grid_scale: grid_scale,
            territories: FnvHashMap::default(),
            territory_id_counter: 0,
        })
    }

    // delete world
    #[wasm_bindgen]
    pub fn clear(&mut self) {
        self.grid.clear();
        self.grid_occupied_coords.clear();
        self.territories.clear();
    }

    #[wasm_bindgen(js_name=getTerritoryIdCounter)]
    pub fn get_territory_id_counter(&self) -> u32 {
        self.territory_id_counter
    }

    #[wasm_bindgen(js_name=setTerritoryIdCounter)]
    pub fn set_territory_id_counter(&mut self, count: u32) {
        self.territory_id_counter = count;
    }

    /// Return new territory id to js
    #[wasm_bindgen(js_name=getNewTerritoryId)]
    pub fn get_new_territory_id(&mut self) -> u32 {
        let new_id = self.territory_id_counter;
        self.territory_id_counter += 1;
        new_id
    }

    /// Create new territory. Return id of new territory.
    /// If input id is None, use a new id.
    #[wasm_bindgen(js_name=createTerritory)]
    pub fn create_territory(&mut self, id: Option<u32>) -> u32 {
        print1("CREATING NEW TERR");
        let id = if let Some(i) = id {
            i
        } else {
            let new_id = self.territory_id_counter;
            self.territory_id_counter += 1;
            new_id
        };
        let territory = Territory::new(id);
        self.territories.insert(id, territory);

        id
    }

    #[wasm_bindgen(js_name=deleteTerritory)]
    pub fn delete_territory(&mut self, id: u32) {
        print1("DELETE TERR");
        // remove territory and its chunks from world grid
        if let Some(territory) = self.territories.get(&id) {
            let chunks = &territory.coords;
            for p in chunks.iter() {
                self.grid_occupied_coords.remove(&p);
                self.grid.remove(&p);
            }

            self.territories.remove(&id);
        }
    }

    #[wasm_bindgen(js_name=getTerritorySize)]
    pub fn get_territory_size(&self, id: u32) -> Option<u32> {
        if let Some(territory) = self.territories.get(&id) {
            return Some(territory.coords.len() as u32);
        }
        return None;
    }

    #[wasm_bindgen(js_name=getTerritoryChunksBuffer)]
    pub fn get_territory_chunks_buffer(&self, id: u32) -> Vec<i32> {
        if let Some(territory) = self.territories.get(&id) {
            return territory.to_buffer();
        }
        return Vec::new();
    }

    #[wasm_bindgen(js_name=getTerritoryBorder)]
    pub fn get_territory_border(&self, id: u32) -> Vec<i32> {
        if let Some(territory) = self.territories.get(&id) {
            return territory.get_border(self.grid_scale);
        }
        return Vec::new();
    }

    #[wasm_bindgen(js_name=listTerritories)]
    pub fn list_territories(&self) {
        for (id, terr) in self.territories.iter() {
            print1(&format!("[{}] terr = {:?}", id, &terr.coords));
        }
    }

    #[wasm_bindgen(js_name=addCoordsToTerritory)]
    pub fn add_coords_to_territory(&mut self, id: u32, coords: Vec<i32>) -> bool {
        print1(&format!("[{}] coords = {:?}", id, coords));

        let mut new_coords: FnvHashSet<Point<i32>> = FnvHashSet::default();
        for i in (0..coords.len()).step_by(2) {
            let x = coords[i];
            let y = coords[i+1];
            let p = Point::new(x, y);

            // add coords that are not occupied in world grid
            if !self.grid_occupied_coords.contains(&p) {
                new_coords.insert(p);
                self.grid_occupied_coords.insert(p);
                self.grid.insert(p, id);
            }
        }
            
        // insert coords into territory
        if let Some(territory) = self.territories.get_mut(&id) {
            territory.insert_coords(new_coords.into_iter());
            return true;
        }

        return false;
    }

    /// Remove coords in world grid from any associated territory
    #[wasm_bindgen(js_name=removeCoords)]
    pub fn remove_coords(&mut self, coords: Vec<i32>) {
        for i in (0..coords.len()).step_by(2) {
            let x = coords[i];
            let y = coords[i+1];
            let p = Point::new(x, y);

            // add coords that are not occupied in world grid
            if self.grid_occupied_coords.contains(&p) {
                self.grid_occupied_coords.remove(&p);
                if let Some(terr_id) = self.grid.remove(&p) {
                    if let Some(terr) = self.territories.get_mut(&terr_id) {
                        terr.coords.remove(&p);
                    }
                }
            }
        }
    }

    #[wasm_bindgen(js_name=addCircleToTerritory)]
    pub fn add_circle_to_territory(&mut self, id: u32, cx: i32, cy: i32, radius: i32) -> bool {
        // ignore 0 or negative radius
		if radius <= 0 {
			return false;
        }
        
        // add radius to territory if it exists
        if let Some(territory) = self.territories.get_mut(&id) {
            // chunks in circle radius
            let mut new_chunks: FnvHashSet<Point<i32>> = FnvHashSet::default();

            // iterate values in grid in range of circle
            for gx in -radius..radius+1 {
                for gy in -radius..radius+1 {
                    let x = cx + gx;
                    let y = cy + gy;
                    if (gx as f32).hypot(gy as f32) < radius as f32 {
                        new_chunks.insert(Point::new(x,y));
                    }
                }
            }

            // subtract chunks already in world grid
            let unoccupied: FnvHashSet<Point<i32>> = new_chunks.difference(&self.grid_occupied_coords).cloned().collect();

            if unoccupied.len() > 0 {
                // mark chunks in global grid
                for chunk in unoccupied.iter() {
                    self.grid.insert(*chunk, id);
                    self.grid_occupied_coords.insert(*chunk);
                }
                territory.insert_coords(unoccupied.into_iter());

                return true;
            }
        }

        return false;
    }

    #[wasm_bindgen(js_name=removeCircleToTerritory)]
    pub fn remove_circle_to_territory(&mut self, id: u32, cx: i32, cy: i32, radius: i32) -> bool {
        // ignore 0 or negative radius
		if radius <= 0 {
			return false;
        }
        
        // add radius to territory if it exists
        if let Some(territory) = self.territories.get_mut(&id) {
            // chunks in circle radius
            let mut circle_chunks: FnvHashSet<Point<i32>> = FnvHashSet::default();

            // iterate values in grid in range of circle
            for gx in -radius..radius+1 {
                for gy in -radius..radius+1 {
                    let x = cx + gx;
                    let y = cy + gy;
                    if (gx as f32).hypot(gy as f32) < radius as f32 {
                        circle_chunks.insert(Point::new(x,y));
                    }
                }
            }

            // subtract chunks not occupied by this territory
            let grid = &self.grid;
            circle_chunks.retain(|&x| if let Some(grid_id) = grid.get(&x) {id == *grid_id} else {false});

            if circle_chunks.len() > 0 {
                // remove chunks in global grid
                for chunk in circle_chunks.iter() {
                    self.grid.remove(chunk);
                    self.grid_occupied_coords.remove(chunk);
                }
                territory.remove_coords(&circle_chunks);

                return true;
            }
        }

        return false;
    }

    // calculate neighboring territories
    // and checks if territory is an "edge" territory
    // (i.e. has coords that do not border another territory)
    #[wasm_bindgen(js_name=calculateNeighbors)]
    pub fn calculate_neighbors(&mut self) {
        for (id, terr) in self.territories.iter_mut() {
            let neighbor_points = terr.get_neighboring_points();

            // calculate territories from neighbor points, and chec
            let mut is_at_edge: bool = false;
            let mut neighbor_territories: FnvHashSet<u32> = FnvHashSet::default();
            for p in neighbor_points.iter() {
                if let Some(id) = self.grid.get(&p) {
                    neighbor_territories.insert(*id);
                }

                if !self.grid_occupied_coords.contains(&p) {
                    is_at_edge = true;
                }
            }

            terr.neighbors = neighbor_territories;
            terr.is_at_edge = is_at_edge;
        }
    }
    
    // return neighbors to territory as vector of territory ids
    #[wasm_bindgen(js_name=getTerritoryNeighbors)]
    pub fn get_territory_neighbors(&self, id: u32) -> Vec<i32> {
        if let Some(territory) = self.territories.get(&id) {
            return Vec::from_iter(territory.neighbors.iter().map(|&v| v as i32))
        }
        return Vec::new();
    }

    // apply graph coloring on territories to generate colors
    // assume planar graph with no loops, no double edges
    #[wasm_bindgen(js_name=generateColors)]
    pub fn generate_colors(&mut self) {
        // reset all colors to none
        for (_, mut terr) in self.territories.iter_mut() {
            terr.color = None;
        }

        let colors = graph_6_coloring(&self.territories);
        
        // write colors to territories
        for (id, color) in colors.iter() {
            if let Some(terr) = self.territories.get_mut(id) {
                terr.color = Some(*color);
            }
        }
    }

    #[wasm_bindgen(js_name=getTerritoryColor)]
    pub fn get_territory_color(&self, id: u32) -> Option<u8> {
        if let Some(territory) = self.territories.get(&id) {
            return territory.color.clone();
        }
        return None;
    }

    #[wasm_bindgen(js_name=getTerritoryIsEdge)]
    pub fn get_territory_is_edge(&self, id: u32) -> Option<bool> {
        if let Some(territory) = self.territories.get(&id) {
            return Some(territory.is_at_edge);
        }
        return None;
    }

    /// Doing in dumb way, just checking all coords in each territory
    #[wasm_bindgen(js_name=getTerritoriesInAABB)]
    pub fn get_territories_in_aabb(&self, xmin: i32, ymin: i32, xmax: i32, ymax: i32) -> Vec<u32> {
        let mut terr_ids: Vec<u32> = Vec::new();

        'terr: for (id, terr) in self.territories.iter() {
            for p in terr.coords.iter() {
                if p.x < xmin || p.x > xmax || p.y < ymin || p.y > ymax {
                    continue;
                }
                // inside
                terr_ids.push(*id);
                continue 'terr;
            }
        }

        return terr_ids;
    }

    /// Merge list of territories into single territory
    /// Return id of the merged territory. This will be the id
    /// of the first element in ids
    #[wasm_bindgen(js_name=mergeTerritories)]
    pub fn merge_territories(&mut self, ids: Vec<u32>) -> Option<u32> {
        // trivial cases, no merge occur
        if ids.len() == 0 {
            return None;
        }
        else if ids.len() == 1 {
            return if self.territories.contains_key(&ids[0]) {
                Some(ids[0])
            } else {
                None
            };
        }

        // make sure that territories all exist
        for id in ids.iter() {
            if !self.territories.contains_key(&id) {
                return None;
            }
        }

        // merge territories into first id
        let merged_id = ids[0];
        let mut merged_terr = self.territories.remove(&merged_id)?;
        // remove old territory, insert chunks into merged territory id
        for id in ids[1..].iter() {
            if let Some(territory) = self.territories.remove(&id) {
                for p in territory.coords.into_iter() {
                    merged_terr.coords.insert(p);
                    self.grid.insert(p, merged_id);
                }
            }
        }

        // reinsert merged territory
        self.territories.insert(merged_id, merged_terr);

        return Some(merged_id);
    }

    /// Subdivide an existing territory into randomly generated territories
    #[wasm_bindgen(js_name=subdivideIntoRandomTerritories)]
    pub fn subdivide_into_random_territories(
        &mut self,
        id: u32,
        average_radius: f64,
        scale_x: f64,
        scale_y: f64,
        random_seed: Option<u32>,
        iterations_improve_center: u32,
        iterations_improve_corner: u32,
        delete_smaller_than: u32,
        merge_smaller_than: u32,
    ) -> Option<Vec<u32>> {
        if let Some(territory) = self.remove_territory(id) {

            // get min/max from territory bounding box, slightly expand it
            let aabb = territory.get_aabb();
            let min = ((aabb.min.x - 1) as f64, (aabb.min.y - 1) as f64);
            let max = ((aabb.max.x + 1) as f64, (aabb.max.y + 1) as f64);

            let mut random_cells: CellDiagram = generate_random_cells(
                average_radius,
                &min,
                &max,
                random_seed,
                iterations_improve_center,
                iterations_improve_corner,
            );

            if scale_x != 1.0 || scale_y != 1.0 {
                // note: this does not properly update centroid positions
                random_cells.scale((scale_x, scale_y));
            }
            random_cells.calculate_bounding_boxes();

            let mut new_territories: Vec<Vec<Point<i32>>> = (0..random_cells.num_cells())
                .map(|_| Vec::new())
                .collect();

            // assign to new territory
            for p in territory.coords.into_iter() {
                if let Some(idx) = random_cells.cell_contains_coords(p.x as f64, p.y as f64) {
                    new_territories[idx].push(p);
                }
            }

            let mut new_territory_ids: Vec<u32> = Vec::new();

            for terr in new_territories.into_iter() {
                if terr.len() > 0 {
                    // skip if delete created territories smaller than size
                    if delete_smaller_than > 0 && terr.len() < delete_smaller_than as usize {
                        continue;
                    }

                    let id = self.create_territory(None);
                    self.add_points_to_territory(id, terr);
                    new_territory_ids.push(id);
                }
            }

            // run merging, if territory smaller than some value
            // merge with SMALLEST neighbor
            if merge_smaller_than > 0 {
                // need to know territory neighbors
                self.calculate_neighbors();

                let merge_size = merge_smaller_than as usize;

                let terr_too_small: Vec<u32> = self.territories.iter()
                    .filter(|(_, v)| v.coords.len() <= merge_size)
                    .map(|(k, _)| k)
                    .cloned()
                    .collect();

                let terr_too_small: Vec<Territory> = terr_too_small.into_iter()
                    .map(|id| self.remove_territory(id).unwrap())
                    .collect();
                
                let mut remaining_territories: Vec<Territory> = Vec::new();

                for terr in terr_too_small.into_iter() {
                    // join first neighbor by default
                    let mut neighbor_to_join = None;
                    let mut neighbor_to_join_size = usize::MAX;

                    // find smallest neighbor thats larger than merge size to join with
                    for id in terr.neighbors.iter() {
                        if let Some(neighbor) = self.territories.get(id) {
                            let neighbor_size = neighbor.coords.len();
                            if neighbor_size < neighbor_to_join_size && neighbor.coords.len() > merge_size {
                                neighbor_to_join = Some(*id);
                                neighbor_to_join_size = neighbor_size;
                            }
                        }
                    }

                    if let Some(neighbor_id) = neighbor_to_join {
                        self.add_points_to_territory(neighbor_id, terr.coords);
                    }
                    else {
                        remaining_territories.push(terr);
                    }
                }

                // territories with no merge candidates, just re-add to world
                // TODO: merge with each other then re-add to world
                // may be expensive, need to recalculate neighbors
                for terr in remaining_territories.into_iter() {
                    self.add_territory(terr);
                }

                // remove invalid new territories
                new_territory_ids.retain(|x| self.territories.contains_key(x));
            }
            
            return Some(new_territory_ids);
        }

        return None;
    }
}


// recursive backtracing loop for coloring nodes
// incredibly slow and should not be used
// returns
// true - if successfully assigned unique color
// false - if failed to assign unique color
fn graph_color_recursive(territories: &mut FnvHashMap<u32, Territory>, node_id: u32) -> bool {

    // get neighbor colors
    let neighbors = territories.get(&node_id).unwrap().neighbors.clone();
    let mut neighbor_colors: [bool; MAX_COLORS] = [false; MAX_COLORS];
    for neighbor_id in neighbors.iter() {
        if let Some(color) = territories.get(&neighbor_id).unwrap().color {
            let color = color as usize;
            if color < MAX_COLORS {
                neighbor_colors[color] = true;
            }
        }
    }

    // print2(&format!("[{}] neighbors: {:?}", node_id, neighbors));
    // print2(&format!("[{}] neighbor colors: {:?}", node_id, neighbor_colors));

    // try assigning color, then assign neighbors
    'colors: for i in 0..MAX_COLORS {
        if neighbor_colors[i] == false {
            // assign node color
            // print2(&format!("[{}]: try color: {}", node_id, i));
            territories.get_mut(&node_id).unwrap().color = Some(i as u8);

            // assign color to un-assigned neighbors
            for neighbor_id in neighbors.iter() {
                if territories.get(&neighbor_id).unwrap().color.is_none() {
                    if graph_color_recursive(territories, *neighbor_id) == false {
                        continue 'colors;
                    }
                }
            }

            return true;
        }
    }

    // failed to assign unique color in path, undo color and return false
    // print2(&format!("failed, undoing color for id {:?}", node_id));
    territories.get_mut(&node_id).unwrap().color = None;
    return false;
}

// graph O(n) 6-coloring strategy
// Given n vertex planar graph G in adjacency list form:
// 
// 1. [Establish degree lists.] For each j where 0 <= j <= n-1, form
// a doubly linked list of all vertices of G of degree j.
// 
// 2. [Label vertices smallest degree last.] For i = n, n-1, n-2, ..., 1
// designate the first vertex of the non-vacuous j degree list of
// smallest j as vertex vi. Delete vi from the j degree list. For each
// vertex v' that was adjacent to vi in G and remains in some degree
// list, say j', delete v' from j' degree list and insert v' in
// the j'-1 degree list.
// 
// 3. [Color vertices.] For i = 1, 2, ..., n, assign vertex vi the
// smallest color value (which must be some integer 1..6) not
// occuring in the vertices adjacent to vi that have already 
// been colored.
// 
// http://i.stanford.edu/pub/cstr/reports/cs/tr/80/830/CS-TR-80-830.pdf
//
// Returns map from territory id -> color integer
fn graph_6_coloring(territories: &FnvHashMap<u32, Territory>) -> FnvHashMap<u32, u8> {

    // graph vertex
    struct Vertex {
        id: u32,
        neighbors: FnvHashSet<u32>,
        current_degree: u32,
        color: Option<usize>, // for processing, this is array index
    }

    // graph as FnvHashMap, id -> vertex
    let mut graph: FnvHashMap<u32, Vertex> = FnvHashMap::default();

    // build degree list (map degree -> Set of vertex ids)
    // BTree map so the first iter entry is always
    // smallest degree
    let mut degree_list: BTreeMap<u32, Vec<u32>> = BTreeMap::new();

    for (id, terr) in territories.iter() {
        let vert = Vertex {
            id: *id,
            neighbors: terr.neighbors.clone(),
            current_degree: terr.neighbors.len() as u32,
            color: None,
        };

        // mark initial vertex degree
        let degree = terr.neighbors.len() as u32;
        if let Some(vertex_list) = degree_list.get_mut(&degree) {
            vertex_list.push(*id);
        }
        else {
            degree_list.insert(degree, vec![*id]);
        }

        graph.insert(*id, vert);
    }

    // iteratively remove vertex with smallest degree

    // list of vertices in order removed by degree
    let mut vert_list_by_degree: Vec<Vertex> = Vec::new();

    // map vertex id to index in vert_list_by_degree so we
    // still have vertex lookup by id
    let mut vert_id_to_vec_index: FnvHashMap<u32, usize> = FnvHashMap::default();
    
    // TODO: loop condition and getting vertex disgusting
    // because map.first_entry() still nightly
    // when api ready, rewrite using first_entry()
    while !degree_list.is_empty() {

        // get current entry degree
        let degree = {
            let (degree, _) = degree_list.iter().next().unwrap();
            *degree
        };

        // get vertex
        let vert = if let Entry::Occupied(mut o) = degree_list.entry(degree) {
            let vert_id = o.get_mut().pop().unwrap();

            if o.get().is_empty() {
                o.remove();
            }

            graph.remove(&vert_id).unwrap()
        }
        else {
            panic!("Failed to get entry even though BTreeMap contains entries");
        };

        // update neighbor degrees that are still in graph
        for neighbor_id in vert.neighbors.iter() {
            if graph.contains_key(neighbor_id) {
                // remove neighbor from current degree list
                let neighbor_degree = graph.get(neighbor_id).unwrap().current_degree;
                let vert_list = degree_list.get_mut(&neighbor_degree).unwrap();

                let idx = vert_list.iter().position(|&x| x == *neighbor_id).unwrap();
                vert_list.swap_remove(idx);

                // if current degree vertex list is len 0, remove from degree list
                if vert_list.is_empty() {
                    degree_list.remove(&neighbor_degree);
                }

                // reduce degree and re-add to degree-1 list
                let neighbor_degree = neighbor_degree - 1;
                graph.get_mut(neighbor_id).unwrap().current_degree = neighbor_degree;
                if let Some(vertex_list) = degree_list.get_mut(&neighbor_degree) {
                    vertex_list.push(*neighbor_id);
                }
                else {
                    degree_list.insert(neighbor_degree, vec![*neighbor_id]);
                }
            }
        }
        
        // insert removed vertex to list
        vert_id_to_vec_index.insert(vert.id, vert_list_by_degree.len());
        vert_list_by_degree.push(vert);
    }

    // color vertices so that no adjacent vertex has same color
    // color in order of fewest neighbors last (reverse iterator)
    let mut colors: FnvHashMap<u32, u8> = FnvHashMap::default();

    'outer: for i in (0..vert_list_by_degree.len()).rev() {

        // get neighbor colors
        let mut neighbor_colors: [bool; 6] = [false; 6];

        for neighbor_id in vert_list_by_degree[i].neighbors.iter() {
            let neighbor_index = vert_id_to_vec_index.get(&neighbor_id).unwrap();
            if let Some(color) = vert_list_by_degree.get(*neighbor_index).unwrap().color {
                let color = color;
                neighbor_colors[color] = true;
            }
        }

        // assign first free color
        for c in 0..6 {
            if neighbor_colors[c] == false {
                let vert = &mut vert_list_by_degree[i];
                vert.color = Some(c);
                colors.insert(vert.id, c as u8);
                continue 'outer;
            }
        }
    }

    return colors;
}

// TODO: solve graph with 5-coloring
// apply linear time 5 coloring algorithm:
// https://en.wikipedia.org/wiki/Five_color_theorem#Linear_time_five-coloring_algorithm
// http://people.math.gatech.edu/~thomas/PAP/fcstoc.pdf
// http://i.stanford.edu/pub/cstr/reports/cs/tr/80/830/CS-TR-80-830.pdf
// fn graph_5_coloring() {
    
//     // graph vertex
//     struct Vertex {
//         id: u32,
//         neighbors: FnvHashSet<u32>,
//         merged: FnvHashSet<u32>,
//     }

//     // build clone of territories graph
//     let mut graph: FnvHashMap<u32, Vertex> = FnvHashMap::default();

//     // running list of vertices with degree < 5
//     let mut vert_max_deg_4: Vec<u32> = Vec::new();

//     // running list of vertices of degree = 5
//     let mut vert_deg_5: Vec<u32> = Vec::new();

//     // vertices removed from graph in order of removal
//     let mut vert_removed: Vec<Vertex> = Vec::new();

//     // first pass, add vertices to stacks matching conditions
//     // and build clone of graph
//     for (id, terr) in self.territories.iter() {
//         let degree = terr.neighbors.len();
//         if degree <= 4 {
//             vert_max_deg_4.push(*id);
//         }
//         else if degree == 5 {
//             vert_deg_5.push(*id);
//         }

//         graph.insert(*id, Vertex {
//             id: *id,
//             neighbors: terr.neighbors.clone(),
//             merged: FnvHashSet::default(),
//         });
//     }

//     // iteratively remove nodes from graph
//     while graph.len() > 0 {
//         // process vertices with deg < 5
//         while let Some(vert_id) = vert_max_deg_4.pop() {
//             // remove vert from graph
//             let vert = graph.remove(&vert_id).unwrap();
            
//             // each neighbor degree reduced by 1
//             // add neighbors to deg 4 or deg 5 stacks if they meet condition
//             for neighbor_id in vert.neighbors.iter() {
//                 let neighbor = graph.get_mut(&neighbor_id).unwrap();
//                 neighbor.neighbors.remove(&vert_id);

//                 // update neighbor degrees
//                 let degree = neighbor.neighbors.len();
//                 if degree < 4 && !vert_max_deg_4.contains(neighbor_id) {
//                     vert_max_deg_4.push(*neighbor_id);
//                 }
//                 else if degree == 4 {
//                     // previous degree must have been 5, remove from that stack
//                     // and add to max deg 4 stack
//                     let idx = vert_deg_5.iter().position(|&x| x == *neighbor_id).unwrap();
//                     vert_deg_5.swap_remove(idx);
//                     vert_max_deg_4.push(*neighbor_id);
//                 }
//                 else if degree == 5 {
//                     vert_deg_5.push(*neighbor_id);
//                 }

//             }

//             // mark removed vert
//             vert_removed.push(vert);
//         }

//         // process vertices with deg == 5
//         // TODO
//         while let Some(vert_id) = vert_deg_5.pop() {
//             // remove vert from graph
//             let vert = graph.remove(&vert_id).unwrap();
//             // TODO
//         }
//     }
// }