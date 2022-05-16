/// Functions for random generating territory cells.
/// Emits CellDiagram which contain smoothed Voronoi-based
/// randomly generated cells.
/// 
/// Uses custom internal Point2D which is a (f64, f64) point
/// with custom Hash/Eq implementation. This must be used until
/// #!specialization rust feature is mature enough to override 
/// default Hash/Eq derive implementation for f64.
/// 
/// See:
/// http://www-cs-students.stanford.edu/%7Eamitp/game-programming/polygon-map-generation/
/// https://en.wikipedia.org/wiki/Lloyd%27s_algorithm

use std::hash::{Hash, Hasher};
use std::collections::HashMap;
use voronator::{VoronoiDiagram, delaunator::Point as VoronoiPoint};
use rand::prelude::*;
use rand::distributions::Uniform;
use std::mem;
use territory::geometry::{AABB, Point};


fn integer_decode(val: f64) -> (u64, i16, i8) {
    let bits: u64 = unsafe { mem::transmute(val) };
    let sign: i8 = if bits >> 63 == 0 { 1 } else { -1 };
    let mut exponent: i16 = ((bits >> 52) & 0x7ff) as i16;
    let mantissa = if exponent == 0 {
        (bits & 0xfffffffffffff) << 1
    } else {
        (bits & 0xfffffffffffff) | 0x10000000000000
    };

    exponent -= 1023 + 52;
    (mantissa, exponent, sign)
}

#[derive(Debug, Clone, PartialEq)]
pub struct Point2D {
    pub x: f64,
    pub y: f64,
}

impl Eq for Point2D {}

impl Hash for Point2D {
    fn hash<H: Hasher>(&self, state: &mut H) {
        let (mantissa_x, exponent_x, sign_x) = integer_decode(self.x);
        let (mantissa_y, exponent_y, sign_y) = integer_decode(self.y);

        mantissa_x.hash(state);
        exponent_x.hash(state);
        sign_x.hash(state);
        mantissa_y.hash(state);
        exponent_y.hash(state);
        sign_y.hash(state);
    }
}

impl Point2D {
    pub fn new(x: f64, y: f64) -> Point2D {
        Point2D {
            x: x,
            y: y,
        }
    }
}

pub struct Corner {
    pub point: Point2D,
    pub is_border: bool,
}

impl Corner {
    pub fn new(point: Point2D, is_border: bool) -> Corner {
        Corner {
            point: point,
            is_border: is_border,
        }
    }
}

// triangle winding orientation
#[derive(Debug, PartialEq, Eq, Clone)]
enum Orientation {
    Collinear,
    CW, // clockwise
    CCW, // counterclockwise
}

// Cell diagram with flattened point lookups
pub struct CellDiagram {
    pub centroids: Vec<Point2D>,    // cell centroid points
    pub corners: Vec<Corner>,       // all corner points (accessed by index lookup)
    pub corner_to_index: HashMap<Point2D, usize>,
    pub neighbors: Vec<Vec<usize>>, // neighbor centers to a corner (all centers that share this corner)
    pub cells: Vec<Vec<usize>>,     // cells containing corner point indices
    pub aabbs: Option<Vec<AABB<f64>>>,   // cell bounding boxes, must be computed after creating cells
}

impl CellDiagram {
    pub fn from_voronoi_diagram(voronoi: VoronoiDiagram, min: &(f64, f64), max: &(f64, f64)) -> CellDiagram {
        // for detecting if points on border of voronoi diagram range
        const EPS: f64 = 1e-6;

        let centroids: Vec<Point2D> = voronoi.cells.iter()
            .map(|cell| centroid_from_points(&cell))
            .map(|p| Point2D::new(p.x, p.y))
            .collect();
        let mut corners: Vec<Corner> = Vec::new();      // all corner points (accessed by index lookup)
        let mut corner_to_index: HashMap<Point2D, usize> = HashMap::new();
        let mut neighbors: Vec<Vec<usize>> = Vec::new(); // neighbor centers to a corner (all centers that share this corner)
        let mut cells: Vec<Vec<usize>> = Vec::new();     // cells containing corner point indices in counter clockwise order

        for (i, cell) in voronoi.cells.iter().enumerate() {
            let mut cell_indices: Vec<usize> = Vec::new();
            for p in cell.iter() {
                let p2 = Point2D::new(p.x, p.y);
                if corner_to_index.contains_key(&p2) {
                    let index = *corner_to_index.get(&p2).unwrap();
                    cell_indices.push(index);
                    neighbors[index].push(i);
                }
                else {
                    let index = corners.len();
                    corner_to_index.insert(p2.clone(), index);

                    let is_border = (p2.x - min.0).abs() < EPS || (p2.y - min.1).abs() < EPS || (p2.x - max.0).abs() < EPS || (p2.y - max.1).abs() < EPS;
                    corners.push(Corner::new(p2, is_border));

                    cell_indices.push(index);
                    neighbors.push(vec![i]);
                }
            }
            cells.push(cell_indices);
        }

        CellDiagram {
            centroids: centroids,
            corners: corners,
            corner_to_index: corner_to_index,
            neighbors: neighbors,
            cells: cells,
            aabbs: None,
        }
    }

    pub fn num_cells(&self) -> usize {
        self.cells.len()
    }

    // stretches cell diagram coords, recalculates centroids
    pub fn scale(&mut self, s: (f64, f64)) {
        // find cell diagram global AABB and mid point
        // initialize to first point in corners
        let p0 = &self.corners[0].point;
        let mut min = p0.clone();
        let mut max = p0.clone();

        for corner in self.corners[1..].iter() {
            let p = &corner.point;
            if p.x < min.x {
                min.x = p.x;
            }
            else if p.x > max.x {
                max.x = p.x;
            }
            
            if p.y < min.y {
                min.y = p.y;
            }
            else if p.y > max.y {
                max.y = p.y;
            }
        }

        let origin = Point2D::new((max.x + min.x)/2.0, (max.y + min.y)/2.0);
        
        // scale corner points
        for corner in self.corners.iter_mut() {
            corner.point = Point2D::new(
                (corner.point.x - origin.x) * s.0 + origin.x,
                (corner.point.y - origin.y) * s.1 + origin.y,
            );
        }

        // TODO: recalculate centroids
    }

    // Note: does not check to make sure cell has at least 2 points
    // This should be ensured by cell diagram creation.
    pub fn calculate_bounding_boxes(&mut self) {
        let mut aabbs: Vec<AABB<f64>> = Vec::with_capacity(self.cells.len());
        for cell in self.cells.iter() {
            // initialize to first point in cell
            let p0 = &self.corners[cell[0]].point;
            let mut min = p0.clone();
            let mut max = p0.clone();

            for idx in cell[1..].iter().cloned() {
                let p = &self.corners[idx].point;
                if p.x < min.x {
                    min.x = p.x;
                }
                else if p.x > max.x {
                    max.x = p.x;
                }
                
                if p.y < min.y {
                    min.y = p.y;
                }
                else if p.y > max.y {
                    max.y = p.y;
                }
            }
            aabbs.push(AABB::new(
                Point::new(min.x, min.y),
                Point::new(max.x, max.y),
            ));
        }

        self.aabbs = Some(aabbs);
    }

    /// Determine if any cell contains point from coords (x,y)
    /// Returns index of cell that contains a point or None if none does
    /// AABBs, cell edges must have been pre-calculated. If either is not
    /// this will always return None.
    /// 
    /// 1. Perform AABB test to see if point lies in cell AABB
    /// 2. Perform horizontal line raycast, count # of intersections
    ///    with cell edges:
    ///    - Even # of hits = outside polygon
    ///    - Odd # of hits = inside polygon
    /// 3. Return first cell that point lies inside, otherwise return None
    pub fn cell_contains_coords(&self, x: f64, y: f64) -> Option<usize> {
        // generic tri orientation detection
        // Returns orientation for point triplet (p, q, r)
        fn orientation(p: &Point2D, q: &Point2D, r: &Point2D) -> Orientation {
            const EPS: f64 = 1e-8;
            let o = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y);

            if o.abs() < EPS {
                return Orientation::Collinear;
            }

            if o > 0. {
                Orientation::CW
            } else {
                Orientation::CCW
            }
        }

        // simplified when p-q is a horizontal line: p.y == q.y
        // for horizontal raycasting
        fn orientation_horz(p: &Point2D, q: &Point2D, r: &Point2D) -> Orientation {
            const EPS: f64 = 1e-7;
            let o = (q.x - p.x) * (r.y - q.y);

            if o.abs() < EPS {
                return Orientation::Collinear;
            }

            if o < 0. {
                Orientation::CW
            } else {
                Orientation::CCW
            }
        }

        // horizontal intersect test between horizontal segment p1-q1 (p1.y == q1.y) 
        // and arbitrary line segment p2-q2
        fn horizontal_intersection(p1: &Point2D, q1: &Point2D, p2: &Point2D, q2: &Point2D) -> bool {
            let o1 = orientation_horz(p1, q1, p2);
            let o2 = orientation_horz(p1, q1, q2);
            let o3 = orientation(p2, q2, p1);
            let o4 = orientation(p2, q2, q1);

            // general case (non colinear points), checks windings (detects if points on left/right sides)
            if o1 != o2 && o3 != o4 {
                return true;
            }

            false
        }

        if self.aabbs.is_none() {
            return None;
        }

        const EPS: f64 = 1e-8;

        let aabbs = self.aabbs.as_ref().unwrap();

        // point we are testing
        let p1 = Point2D::new(x, y);

        for (i, cell) in self.cells.iter().enumerate() {
            if aabbs[i].contains_xy(x, y) {
                // num of raycast intersections with cell edges
                let mut num_intersects: u32 = 0;
                let mut point_is_on_edge: bool = false;

                // form horizontal line from centroid to point outside of cell
                // let c = &self.centroids[i];

                // horizontal anchor point outside of bounding box
                let q1 = Point2D::new(aabbs[i].min.x - 1.0, y);

                // simplified segment intersection for horizontal lines:
                for (j, idx1) in cell.iter().cloned().enumerate() {
                    let idx2 = if j < cell.len() - 1 {
                        cell[j+1]
                    } else {
                        cell[0]
                    };

                    // occasionally get duplicate points from voronoi
                    if idx1 == idx2 {
                        continue;
                    }

                    let p2 = &self.corners[idx1].point;
                    let q2 = &self.corners[idx2].point;

                    // special case: point on horizontal edge
                    if (y - p2.y).abs() < EPS && (y - q2.y) < EPS && ( (x >= p2.x && x <= q2.x) || (x >= q2.x && x <= p2.x) ) {
                        point_is_on_edge = true;
                        break;
                    }
                    // special case: point on vertical edge
                    if (x - p2.x).abs() < EPS && (x - q2.x) < EPS && ( (y >= p2.y && y <= q2.y) || (y >= q2.y && y <= p2.y) ) {
                        point_is_on_edge = true;
                        break;
                    }
                    else if horizontal_intersection(&p1, &q1, p2, q2) {
                        num_intersects += 1;
                    }
                }

                // if odd, point is inside
                if point_is_on_edge || num_intersects % 2 == 1 {
                    return Some(i);
                }
            }
        }

        return None;
    }
}

// https://en.wikipedia.org/wiki/Centroid#Of_a_polygon
fn centroid_from_points(points: &Vec<VoronoiPoint>) -> VoronoiPoint {
    let mut cx: f64 = 0.;
    let mut cy: f64 = 0.;
    let mut area: f64 = 0.;
    for i in 0..points.len() {
        let p1 = &points[i];
        let p2 = if i < points.len() - 1 {
            &points[i+1]
        } else {
            &points[0]
        };
        let a = p1.x * p2.y - p2.x * p1.y;
        cx += (p1.x + p2.x) * a;
        cy += (p1.y + p2.y) * a;
        area += a;
    }
    let a6 = 6. * 0.5 * area;

    VoronoiPoint {
        x: cx/a6,
        y: cy/a6,
    }
}

fn centroid_from_corners_lookup(corners: &Vec<Corner>, lookup: &Vec<usize>) -> Point2D {
    let mut cx: f64 = 0.;
    let mut cy: f64 = 0.;
    let mut area: f64 = 0.;
    for i in 0..lookup.len() {
        let i1 = &lookup[i];
        let i2 = if i < lookup.len() - 1 {
            &lookup[i+1]
        } else {
            &lookup[0]
        };
        
        let p1 = &corners[*i1].point;
        let p2 = &corners[*i2].point;

        let a = p1.x * p2.y - p2.x * p1.y;
        cx += (p1.x + p2.x) * a;
        cy += (p1.y + p2.y) * a;
        area += a;
    }
    let a6 = 6. * 0.5 * area;

    Point2D::new(cx/a6, cy/a6)
}

/// Improve cell corners by setting to average of adjacent
/// cell centroids.
/// 
/// Based on amitp / Red Blob Games:
/// https://github.com/amitp/mapgen2/blob/master/Map.as
fn smooth_corners(mut cell_diagram: CellDiagram) -> CellDiagram {
    // first we compute the average of the centers next to each corner.
    for (i, corner) in cell_diagram.corners.iter_mut().enumerate() {
        // check if point is border
        if corner.is_border {
            continue;
        }
        else {
            let mut new_point = VoronoiPoint {x: 0.0, y: 0.0 };
            for idx in cell_diagram.neighbors[i].iter().cloned() {
                let r = &cell_diagram.centroids[idx];
                new_point.x += r.x;
                new_point.y += r.y;
            }
            let num_neighbors = cell_diagram.neighbors[i].len() as f64;
            corner.point.x = new_point.x / num_neighbors;
            corner.point.y = new_point.y / num_neighbors;
        }
    }

    // recalculate centroids
    for (i, c) in cell_diagram.centroids.iter_mut().enumerate() {
        let new_c = centroid_from_corners_lookup(&cell_diagram.corners, &cell_diagram.cells[i]);
        c.x = new_c.x;
        c.y = new_c.y;
    }

    return cell_diagram;
}

/// Improve randomly generated points using Lloyd Relaxation.
/// Input is a voronoi diagram.
/// Loop:
///    Calculate voronoi from points
///    Calculate voronoi cell centroids
///    Set points from centroids
/// 
/// Repeat until cells become more uniform
fn smooth_centers(voronoi: &VoronoiDiagram, min: &(f64, f64), max: &(f64, f64)) -> Option<VoronoiDiagram> {
    let points: Vec<VoronoiPoint> = voronoi.cells.iter()
        .map(|cell| centroid_from_points(&cell))
        .collect();

    VoronoiDiagram::new(&VoronoiPoint {x: min.0, y: min.1}, &VoronoiPoint {x: max.0, y: max.1}, &points)
}


/// Generate random number of cells based on average expected radius of cells.
/// Determines number of points by assuming cells are circles with average_radius
/// and counting how many cells fit in the [min, max] area.
/// 
/// Cells are generated as voronois then smoothed with `iterations_smooth_center`
/// using Lloyd Relaxation followed by `iterations_smooth_corner` of averaging
/// the corner points to adjacent cell centroids.
pub fn generate_random_cells(
    average_radius: f64,
    min: &(f64, f64),
    max: &(f64, f64),
    random_seed: Option<u32>,
    iterations_smooth_center: u32,
    iterations_smooth_corner: u32,
) -> CellDiagram {
    let mut rng = if let Some(seed) = random_seed {
        rand::rngs::SmallRng::seed_from_u64(seed as u64)
    } else {
        rand::rngs::SmallRng::from_entropy()
    };

    // determine num points by dividing area by expected circular area of each cell
    // require minimum of 3 points to create voronoi diagram
    let cell_avg_area = 3.141592654 * average_radius * average_radius;
    let npoints = ((max.0 - min.0) * (max.1 - min.1) / cell_avg_area).round().max(3.0) as u32;

    let range_x = Uniform::new(min.0, max.0);
    let range_y = Uniform::new(min.1, max.1);
    let points: Vec<(f64, f64)> = (0..npoints)
        .map(|_| (rng.sample(&range_x), rng.sample(&range_y)))
        .collect();
    
    // initial voronoi diagram
    let mut voronoi: VoronoiDiagram = VoronoiDiagram::from_tuple(&min, &max, &points).unwrap();
    
    // smooth centers
    for _ in 0..iterations_smooth_center {
        voronoi = smooth_centers(&voronoi, &min, &max).unwrap();
    }

    let mut cell_diagram = CellDiagram::from_voronoi_diagram(voronoi, min, max);
    for _ in 0..iterations_smooth_corner {
        cell_diagram = smooth_corners(cell_diagram);
    }

    cell_diagram
}