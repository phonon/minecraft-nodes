/// territory.rs
/// ----------------------------------------------------------------
/// Territory manipulation functionality
/// 
/// https://en.wikipedia.org/wiki/Maze_solving_algorithm#Wall_follower

extern crate bitflags;
extern crate wasm_bindgen;

use territory::geometry::{AABB, Point};
use territory::polygon::get_core;
use wasm_bindgen::prelude::*;
use fnv::{FnvHashSet};

#[wasm_bindgen]
extern {
	#[wasm_bindgen(js_namespace = console)]
	fn log(msg: &str);

	#[wasm_bindgen(js_namespace = console)]
	fn error(msg: &str);
}

fn log1(s: &str) {
	//log(s);
}

fn log2(s: &str) {
	//log(s);
}

pub struct Territory {
    pub id: u32,
	pub coords: FnvHashSet<Point<i32>>,
	pub neighbors: FnvHashSet<u32>,   // neighboring territory ids
	pub color: Option<u8>,            // color id, None if not assigned
	pub is_at_edge: bool              // territory is at the edge (has coords that do not border territories)
}

impl Territory {
    pub fn new(id: u32) -> Territory {
        Territory {
            id: id,
            coords: FnvHashSet::default(),
			neighbors: FnvHashSet::default(),
			color: None,
			is_at_edge: false,
        }
	}
	
	pub fn to_buffer(&self) -> Vec<i32> {
		let mut buffer = Vec::with_capacity(2 * self.coords.len());
		for p in self.coords.iter() {
			buffer.push(p.x);
			buffer.push(p.y);
		}
		return buffer;
	}

	// insert a Set of coords into the territory
	// -> performs union operation internally
	// return status of whether operation successful
	pub fn insert_coords<T: IntoIterator<Item = Point<i32>>>(&mut self, coords: T) {
		self.coords.extend(coords);
	}

	// remove set of coords into the territory
	// -> performs union operation internally
	// return status of whether operation successful
	pub fn remove_coords(&mut self, coords: &FnvHashSet<Point<i32>>) {
		for p in coords.iter() {
			self.coords.remove(p);
		}
	}

	pub fn get_aabb(&self) -> AABB<i32> {
		AABB::from_points(self.coords.iter().cloned())
	}
	
	// return buffer with border from coords in territory
	// output buffer format: [ [N] [meta1] [coords1] [edge1] [meta2] [coords2] [edge2] ... ]
	// [
	//    cx, cy,             core center coords (cx, cy)
	//    N,                  num of border loops,
	//    n1, e1,             num border points, edge loop points
	//    x(1,1), y(1,1),     border coords (loop 1)   
	//    x(1,2), y(1,2),
	//    ...
	//    ex(1,1), ey(1,1),   edge points (loop 1)
	//    ex(1,2), ey(1,2),
	//    ...
	//    n2, e2,             
	//    x(2,1), y(2,1),     border coords (loop 2)
	//    x(2,2), y(2,2),
	//    ...
	//    ex(2,1), ey(2,1),   edge points (loop 2)
	//    ex(2,2), ey(2,2),
	//    ...
	// ]
	pub fn get_border(&self, grid_scale: i32) -> Vec<i32> {

		// empty region
		if self.coords.len() == 0 {
			return vec![0, 0, 0];
		}

		// get bounding box of coords
		let mut xmin = std::i32::MAX;
		let mut xmax = std::i32::MIN;
		let mut ymin = std::i32::MAX;
		let mut ymax = std::i32::MIN;
		for chunk in self.coords.iter() {
			let x = chunk.x;
			let y = chunk.y;
			if x < xmin { xmin = x };
			if x > xmax { xmax = x };
			if y < ymin { ymin = y };
			if y > ymax { ymax = y };
		}

		log1(&format!("{}, {}, {}, {}", xmin, xmax, ymin, ymax));

		// grid, with zero padding on each side
		let size_x = (3 + xmax - xmin) as usize;
		let size_y = (3 + ymax - ymin) as usize;
		let mut grid = vec![vec![false; size_y]; size_x];
		log1(&format!("grid[{}][{}]",size_x, size_y));

		// set values in grid
		for chunk in self.coords.iter() {
			let gx = (1 - xmin + chunk.x) as usize;
			let gy = (1 - ymin + chunk.y) as usize;
			grid[gx][gy] = true;
		}

		// find border points
		let mut border: Vec<Point<i32>> = Vec::new();
		for chunk in self.coords.iter() {
			let x = chunk.x;
			let y = chunk.y;
			let gx = (1 - xmin + x) as usize;
			let gy = (1 - ymin + y) as usize;

			// check if adjacent is empty
			if !grid[gx-1][gy] || !grid[gx+1][gy] || !grid[gx][gy-1] || !grid[gx][gy+1] {
				let p = Point::new(x, y);
				if !border.contains(&p) {
					border.push(p);
				}
			}
		}

		// =============================================
		// CLUSTER FINDING
		// separate border points into connected clusters,
		// using sweep line based algo
		// 1. sort border points by x coord
		// 2. store each border cluster as set of points
		//    with a bounding box [xmax, ymin, ymax], xmin unbounded
		// 3. sweep across x coords for each point p:
		//     2a. find up to 2 clusters p is part of (do bbox check then adjacency check)
		//     2b. add to 1 cluster, updating bounding box
		//     2c. if another cluster possible, merge the two clusters, and sort points by x value
		// =============================================
		
		// contains a collection of x-sorted coordinates in a border
		// and a bounding box for potentially adjacent coordinates.
		// [xmax, ymin, ymax] bounding box, uses actual bbox of coords + 1
		// so it is actually [xmax+1, ymin-1, ymax+1] of the point set
		struct BorderPointCluster {
			points: Vec<Point<i32>>,
			xmax: i32,
			ymin: i32,
			ymax: i32,
		}

		impl BorderPointCluster {
			fn new(points: Vec<Point<i32>>, xmax: i32, ymin: i32, ymax: i32) -> BorderPointCluster {
				BorderPointCluster {
					points: points,
					xmax: xmax,
					ymin: ymin,
					ymax: ymax,
				}
			}
		}

		border.sort_by(|a, b| a.x.partial_cmp(&b.x).unwrap());
	
		let mut clusters: Vec<BorderPointCluster> = Vec::new();
		
		for p in border.into_iter() {
			// check if point can be inserted into multiple clusters
			let mut cluster_to_insert_1: i32 = -1; // none
			let mut cluster_to_insert_2: i32 = -1; // none
	
			'find_clusters: for (k, cluster) in clusters.iter().enumerate() {
				
				// check if point inside cluster bounding box
				if p.x <= cluster.xmax && p.y >= cluster.ymin && p.y <= cluster.ymax {
					for p_prev in cluster.points.iter().rev() {
						// only check previous adjacent x values
						if p_prev.x < p.x - 1 {
							break;
						}
						
						if points_are_adjacent_border(p, *p_prev, &grid, xmin, ymin) {
							if cluster_to_insert_1 == -1 {
								cluster_to_insert_1 = k as i32;
								break;
							}
							else {
								cluster_to_insert_2 = k as i32;
								break 'find_clusters;
							}
						}
					}
				}
			}
	
			// insert into existing
			if cluster_to_insert_1 != -1 {
				let mut idx1 = cluster_to_insert_1 as usize;
				clusters[idx1].points.push(p);
				clusters[idx1].xmax = p.x + 1; // x sorted, so always >= existing
				clusters[idx1].ymin = clusters[idx1].ymin.min(p.y - 1);
				clusters[idx1].ymax = clusters[idx1].ymax.max(p.y + 1);
	
				// part of another cluster, merge these two together
				if cluster_to_insert_2 != -1 {
					let idx2 = cluster_to_insert_2 as usize;
					if idx1 == clusters.len() - 1 {
						idx1 = idx2;
					}
	
					let mut cluster_to_merge = clusters.swap_remove(idx2);
					clusters[idx1].points.append(&mut cluster_to_merge.points);
					clusters[idx1].xmax = clusters[idx1].xmax.max(cluster_to_merge.xmax);
					clusters[idx1].ymin = clusters[idx1].ymin.min(cluster_to_merge.ymin);
					clusters[idx1].ymax = clusters[idx1].ymax.max(cluster_to_merge.ymax);
	
					// sort points
					clusters[idx1].points.sort_by(|a, b| a.x.partial_cmp(&b.x).unwrap());
				}
			}
			// create new cluster
			else {
				clusters.push(BorderPointCluster::new(
					vec![p],
					p.x + 1,
					p.y - 1,
					p.y + 1,
				));
			}
		}

		// =============================================
		// FORM EDGE LOOPS
		// create edge loop from each cluster of border coords
		// create by supersampling the grid by making each coord into
		// four superimposed coordinates:
		//            .   .
		// o  ->        o
		//            .   .
		let grid_scale_2 = grid_scale / 2;
		let grid_offset = grid_scale_2; // offset to align grid, default = grid_scale/2
		let mut largest_loop_index = 0;
		let mut largest_loop_size = 0;
		let mut border_loops: Vec<Vec<Point<i32>>> = Vec::new();

		for c in clusters.iter() {
			log1(&format!("CLUSTER {}", c.points.len()));
			let mut edges: Vec<Vec<Point<i32>>> = Vec::new();
			// clusters found by adjacent scanning so the points
			// are ordered: iteratively link and form edge loop
			// edges are vectors of points
			for p in c.points.iter() {
				let gx = (1 - xmin + p.x) as usize;
				let gy = (1 - ymin + p.y) as usize;

				let node_type = Edge::NONE;
				let node_type = if !grid[gx][gy-1] { node_type | Edge::N } else { node_type };
				let node_type = if !grid[gx][gy+1] { node_type | Edge::S } else { node_type };
				let node_type = if !grid[gx-1][gy] { node_type | Edge::W } else { node_type };
				let node_type = if !grid[gx+1][gy] { node_type | Edge::E } else { node_type };

				match node_type {
					Edge::N => {
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
						]);
					},
					Edge::S => {
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::E => {
						edges.push(vec![
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::W => {
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::NS => {
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
						]);
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::EW => {
						edges.push(vec![
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::NE => {
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::NW => {
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
						]);
					},
					Edge::SE => {
						edges.push(vec![
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::SW => {
						edges.push(vec![
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
						]);
					},
					Edge::ENW => {
						edges.push(vec![
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::ESW => {
						edges.push(vec![
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
						]);
					},
					Edge::NES => {
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::NWS => {
						edges.push(vec![
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
						]);
					},
					Edge::NESW => {
						edges.push(vec![
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y - grid_scale_2),
							Point::new(grid_scale * p.x + grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y + grid_scale_2),
							Point::new(grid_scale * p.x - grid_scale_2, grid_scale * p.y - grid_scale_2),
						]);
					},
					_ => (),
				}

				// join all possible edge loops
				edges = join_edge_loops(edges);
			}

			if let Some(edge_loop) = edges.pop() {
				// get loop size
				let loop_size = edge_loop.len();

				// insert loop
				border_loops.push(edge_loop);

				// update largest loop
				if loop_size > largest_loop_size {
					largest_loop_size = loop_size;
					largest_loop_index = border_loops.len() - 1;
				}

			}
			else {
				// this may be bad to have here...
				panic!("Unable to find edge loop...");
			}
		}

		// find centroid from "largest" edge loop (most points)
		// -> assume most territories composed of 1 cluster so that
		//    this heuristic is okay
		let mut largest_loop_as_f32: Vec<Point<f32>> = Vec::new();
		for p in border_loops[largest_loop_index].iter() {
			log2(&format!("{}, {}", p.x, p.y));
			largest_loop_as_f32.push(Point::new(p.x as f32, p.y as f32));
		}
		let core = get_core(&largest_loop_as_f32, 1.0).unwrap();

		// write output buffer
		let mut output_buffer = Vec::new();
		output_buffer.push(grid_offset + (core.x as i32));
		output_buffer.push(grid_offset + (core.y as i32));
		output_buffer.push(clusters.len() as i32);
		for (c, l) in clusters.iter().zip(border_loops.iter()) {
			log1("TEST!");

			output_buffer.push(c.points.len() as i32);
			output_buffer.push(l.len() as i32);
			for p in c.points.iter() {
				output_buffer.push(p.x);
				output_buffer.push(p.y);
			}
			for p in l.iter() {
				output_buffer.push(grid_offset + p.x);
				output_buffer.push(grid_offset + p.y);
			}
		}

		return output_buffer;
	}

	// return coords immediately neighboring this region
	pub fn get_neighboring_points(&self) -> FnvHashSet<Point<i32>> {

		// empty region
		if self.coords.len() == 0 {
			return FnvHashSet::default()
		}

		// get bounding box of coords
		let mut xmin = std::i32::MAX;
		let mut xmax = std::i32::MIN;
		let mut ymin = std::i32::MAX;
		let mut ymax = std::i32::MIN;
		for chunk in self.coords.iter() {
			let x = chunk.x;
			let y = chunk.y;
			if x < xmin { xmin = x };
			if x > xmax { xmax = x };
			if y < ymin { ymin = y };
			if y > ymax { ymax = y };
		}

		log1(&format!("{}, {}, {}, {}", xmin, xmax, ymin, ymax));

		// grid, with zero padding on each side
		let size_x = (3 + xmax - xmin) as usize;
		let size_y = (3 + ymax - ymin) as usize;
		let mut grid = vec![vec![false; size_y]; size_x];
		log1(&format!("grid[{}][{}]",size_x, size_y));

		// set values in grid to these coords
		for chunk in self.coords.iter() {
			let gx = (1 - xmin + chunk.x) as usize;
			let gy = (1 - ymin + chunk.y) as usize;
			grid[gx][gy] = true;
		}

		// calculate neighbor points
		let mut neighbor_points: FnvHashSet<Point<i32>> = FnvHashSet::default();
		for chunk in self.coords.iter() {
			let x = chunk.x;
			let y = chunk.y;
			let gx = (1 - xmin + x) as usize;
			let gy = (1 - ymin + y) as usize;

			if !grid[gx-1][gy] {
				neighbor_points.insert(Point::new(x-1, y));
			} 
			if !grid[gx+1][gy] {
				neighbor_points.insert(Point::new(x+1, y));
			}
			if !grid[gx][gy-1] {
				neighbor_points.insert(Point::new(x, y-1));
			}
			if !grid[gx][gy+1] {
				neighbor_points.insert(Point::new(x, y+1));
			}
		}

		return neighbor_points;
	}

}

bitflags! {
	struct Edge: u8 {
		const NONE = 0b0000; // none
		const N = 0b0001; // north
		const S = 0b0010; // south
		const E = 0b0100; // east
		const W = 0b1000; // west
		const NS = Self::N.bits | Self::S.bits;
		const NE = Self::N.bits | Self::E.bits;
		const NW = Self::N.bits | Self::W.bits;
		const SE = Self::S.bits | Self::E.bits;
		const SW = Self::S.bits | Self::W.bits;
		const EW = Self::E.bits | Self::W.bits;
		const ENW = Self::E.bits | Self::N.bits | Self::W.bits;
		const ESW = Self::E.bits | Self::S.bits | Self::W.bits;
		const NES = Self::N.bits | Self::E.bits | Self::S.bits;
		const NWS = Self::N.bits | Self::W.bits | Self::S.bits;
		const NESW = Self::N.bits | Self::E.bits | Self::S.bits | Self::W.bits;
	}
}

// check if two points are adjacent in a grid
fn points_are_adjacent_border(p1: Point<i32>, p2: Point<i32>, grid: &Vec<Vec<bool>>, xmin: i32, ymin: i32) -> bool {
	// check immediate neighbors
	if p1.x == p2.x && ( p1.y == p2.y - 1 || p1.y == p2.y + 1 ) {
		return true;
	}
	else if p1.y == p2.y && ( p1.x == p2.x - 1 || p1.x == p2.x + 1 ) {
		return true;
	}

	// grid values
	let gx1 = (1 - xmin + p1.x) as usize;
	let gy1 = (1 - ymin + p1.y) as usize;
	
	// check diagonal neighbors with connected tile
	if p1.x == p2.x - 1 && p1.y == p2.y - 1 && ( grid[gx1+1][gy1] || grid[gx1][gy1+1] ) {
		return true;
	}
	else if p1.x == p2.x + 1 && p1.y == p2.y - 1 && ( grid[gx1-1][gy1] || grid[gx1][gy1+1] ) {
		return true;
	}
	else if p1.x == p2.x - 1 && p1.y == p2.y + 1 && ( grid[gx1+1][gy1] || grid[gx1][gy1-1] ) {
		return true;
	}
	else if p1.x == p2.x + 1 && p1.y == p2.y + 1 && ( grid[gx1-1][gy1] || grid[gx1][gy1-1] ) {
		return true;
	}

	return false;
}

// for each edge1 in loops:
//    for each other edge2 in loops:
//        if edge1 and edge2 can be joined:
//           edge_new = edge1 join edge2
//           insert edge_new into loops
//           continue outer for loop
//    edge1 has no possible joins, add to finished stack
fn join_edge_loops(mut edge_loops: Vec<Vec<Point<i32>>>) -> Vec<Vec<Point<i32>>> {

	// list of loops without any more possible connections
	let mut no_more_connections: Vec<Vec<Point<i32>>> = Vec::new();

	'outer: while let Some(mut edge1) = edge_loops.pop() {
		let e1_first = edge1.first().unwrap().clone();
		let e1_last= edge1.last().unwrap().clone();

		// edges visited but no connection possible
		let mut visited_edges: Vec<Vec<Point<i32>>> = Vec::new();

		while let Some(mut edge2) = edge_loops.pop() {
			let e2_first = edge2.first().unwrap().clone();
			let e2_last= edge2.last().unwrap().clone();
			
			// join edges if possible
			if e1_first == e2_first {
				edge2.reverse();
				edge2.pop();
				edge2.append(edge1.as_mut());
				edge1 = edge2;

				// re-insert loops into main stack
				edge_loops.append(&mut visited_edges);
				edge_loops.push(edge1);
				continue 'outer;
			}
			else if e1_first == e2_last {
				edge2.pop();
				edge2.append(edge1.as_mut());
				edge1 = edge2;

				// re-insert loops into main stack
				edge_loops.append(&mut visited_edges);
				edge_loops.push(edge1);
				continue 'outer;
			}
			else if e1_last == e2_first {
				edge1.pop();
				edge1.append(edge2.as_mut());

				// re-insert loops into main stack
				edge_loops.append(&mut visited_edges);
				edge_loops.push(edge1);
				continue 'outer;
			}
			else if e1_last == e2_last {
				edge2.reverse();
				edge1.pop();
				edge1.append(edge2.as_mut());

				// re-insert loops into main stack
				edge_loops.append(&mut visited_edges);
				edge_loops.push(edge1);
				continue 'outer;
			}
			else {
				visited_edges.push(edge2);
			}
		}
		
		// could not find any connections to join
		no_more_connections.push(edge1);
		edge_loops = visited_edges;
	}

	return no_more_connections;
}
