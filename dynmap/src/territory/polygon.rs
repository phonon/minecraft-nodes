/// polygon.rs
/// ----------------------------------------------------------------
/// Polygon algorithms:
/// pole of inaccessibility algorithm uses polylabel: https://github.com/urschrei/polylabel-rs
/// and functions taken from its dependency: https://github.com/georust/geo
/// We assume the input polygons are well-defined loops of Points generated
/// from continguous chunk tiles. So we can ignore many error bounds in
/// the original algorithm to simplify/speed up search.

use num_traits::{Bounded, Float, FromPrimitive, Signed};
use std::cmp::Ordering;
use std::collections::BinaryHeap;
use std::iter::Sum;
use thiserror::Error;

use territory::geometry::{Point, AABB};

use wasm_bindgen::prelude::*;

#[wasm_bindgen]
extern {
    #[wasm_bindgen(js_namespace = console)]
    fn log(msg: &str);

    #[wasm_bindgen(js_namespace = console)]
    fn error(msg: &str);
}

fn log1(s: &str) {
    // log(s);
}

/// Possible Polylabel errors
#[derive(Error, Debug, PartialEq)]
#[error("{0}")]
pub enum PolygonError {
    #[error("Couldn't calculate a centroid for the input Polygon")]
    CentroidCalculation,
    #[error("Couldn't calculate a bounding box for the input Polygon")]
    RectCalculation,
    #[error("The priority queue is unexpectedly empty. This is a bug!")]
    EmptyQueue,
}

/// The position of a `Point` with respect to a Line defined by two `Point`s
#[derive(PartialEq, Clone, Debug)]
enum PointPosition {
    OnBoundary,
    Inside,
    Outside,
}

fn line_determinant<T>(start: &Point<T>, end: &Point<T>) -> T
where T: Float
{
    start.x * end.y - start.y * end.x
}

/// Calculate simple area of a polygon with assumptions:
fn get_area<T>(polygon: &Vec<Point<T>>) -> T
where T: Float + FromPrimitive + Sum,
{
    if polygon.is_empty() || polygon.len() == 1 {
        return T::zero()
    }

    let mut twice_signed_ring_area = T::zero();
    for i in 0..polygon.len()-1 {
        let start = polygon[i];
        let end = polygon[i+1];
        twice_signed_ring_area = twice_signed_ring_area + line_determinant(&start, &end);
    }

    twice_signed_ring_area / T::from_i32(2).unwrap()
}

/// Calculate simple centroid with assumptions:
/// - polygon contains no holes
/// - polygon is not a flat line
fn get_centroid<T>(polygon: &Vec<Point<T>>) -> Point<T>
where T: Float + FromPrimitive + Sum,
{
    if polygon.is_empty() {
        return Point::new(T::zero(), T::zero()); // arbitrary value for edge case
    }
    if polygon.len() == 1 {
        return polygon[0].clone();
    }
    else {
        let area = get_area(polygon);
        let mut sum_x = T::zero();
        let mut sum_y = T::zero();
        for i in 0..polygon.len()-1 {
            let start = polygon[i];
            let end = polygon[i+1];
            let tmp = line_determinant(&start, &end);
            sum_x = sum_x + ((end.x + start.x) * tmp);
            sum_y = sum_y + ((end.y + start.y) * tmp);
        }
        let six = T::from_i32(6).unwrap();
        Point::new(sum_x / (six * area), sum_y / (six * area))
    }
}

/// Calculate if `Point` p contained inside a polygon
fn point_position_from_polygon<T>(p: Point<T>, polygon: &Vec<Point<T>>) -> PointPosition
where T: Float
{
    // no points
    if polygon.is_empty() {
        return PointPosition::Outside;
    }

    // point on edge
    if polygon.contains(&p) {
        return PointPosition::OnBoundary;
    }

    let mut xints = T::zero();
    let mut crossings = 0;
    for i in 0..polygon.len()-1 {
        let start = polygon[i];
        let end = polygon[i+1];
        if p.y > start.y.min(end.y) && p.y <= start.y.max(end.y) && p.x <= start.x.max(end.x) {
            if start.y != end.y {
                xints = (p.y - start.y) * (end.x - start.x) / (end.y - start.y) + start.x;
            }
            if ( start.x == end.x ) || ( p.x <= xints ) {
                crossings += 1;
            }
        }
    }
    if crossings % 2 == 1 {
        PointPosition::Inside
    } else {
        PointPosition::Outside
    }
}

// check if polygon contains a point
fn polygon_contains<T>(polygon: &Vec<Point<T>>, p: Point<T>) -> bool 
where T: Float
{
    match point_position_from_polygon(p, polygon) {
        PointPosition::OnBoundary | PointPosition::Outside => false,
        _ => true,
    }
}

// distance from point to line [start, end]
// https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line
fn point_distance_to_line_segment<T>(p: Point<T>, start: Point<T>, end: Point<T>) -> T 
where T: Float
{
    if start == end {
        return p.distance_to(&start);
    }

    let dx = end.x - start.x;
    let dy = end.y - start.y;
    let d2 = dx * dx + dy * dy;

    // check if point beyond line segment or on axis
    let r = ((p.x - start.x) * dx + (p.y - start.y) * dy) / d2;
    if r <= T::zero() {
        return p.distance_to(&start);
    }
    if r >= T::one() {
        return p.distance_to(&end);
    }

    // project point onto normal
    return ((start.y - p.y) * dx - (start.x - p.x) * dy).abs() / d2.sqrt();
}

// shortest distance from point to a connected set of points
fn shortest_distance_to_path<T>(p: Point<T>, path: &Vec<Point<T>>) -> T 
where T: Float
{
    let start = path[0];
    if p == start {
        return T::zero();
    }

    let mut shortest_distance = T::max_value();

    for i in 0..path.len()-1 {
        let start = path[i];
        let end = path[i+1];

        // if point equal to any point in path, return 0.0
        if p == end {
            return T::zero();
        }

        let dist = point_distance_to_line_segment(p, start, end);
        if dist < shortest_distance {
            shortest_distance = dist;
        }
    }

    return shortest_distance;
}


/// Represention of a Quadtree node's cells. A node contains four Qcells.
#[derive(Debug)]
struct Qcell<T>
where
    T: Float + Signed,
{
    // The cell's centroid
    centroid: Point<T>,
    // Half of the parent node's extent
    extent: T,
    // Distance from centroid to polygon
    distance: T,
    // Maximum distance to polygon within a cell
    max_distance: T,
}

impl<T> Qcell<T>
where
    T: Float + Signed,
{
    fn new(x: T, y: T, h: T, distance: T, max_distance: T) -> Qcell<T> {
        Qcell {
            centroid: Point::new(x, y),
            extent: h,
            distance,
            max_distance,
        }
    }
}

impl<T> Ord for Qcell<T>
where
    T: Float + Signed,
{
    fn cmp(&self, other: &Qcell<T>) -> std::cmp::Ordering {
        self.max_distance.partial_cmp(&other.max_distance).unwrap()
    }
}
impl<T> PartialOrd for Qcell<T>
where
    T: Float + Signed,
{
    fn partial_cmp(&self, other: &Qcell<T>) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}
impl<T> Eq for Qcell<T> where T: Float + Signed {}
impl<T> PartialEq for Qcell<T>
where
    T: Float + Signed,
{
    fn eq(&self, other: &Qcell<T>) -> bool
    where
        T: Float,
    {
        self.max_distance == other.max_distance
    }
}

/// Signed distance from a Qcell's centroid to a Polygon's outline
/// Returned value is negative if the point is outside the polygon's exterior ring
fn signed_distance<T>(x: T, y: T, polygon: &Vec<Point<T>>) -> T
where
    T: Float,
{
    let point = Point::new(x, y);
    let inside = polygon_contains(polygon, point);
    // Use LineString distance, because Polygon distance returns 0.0 for inside
    let distance = shortest_distance_to_path(point, polygon);
    if inside {
        distance
    } else {
        -distance
    }
}

/// Add a new Quadtree node made up of four `Qcell`s to the binary heap
fn add_quad<T>(
    mpq: &mut BinaryHeap<Qcell<T>>,
    cell: &Qcell<T>,
    new_height: &T,
    polygon: &Vec<Point<T>>,
) where
    T: Float + Signed,
{
    let two = T::one() + T::one();
    let centroid_x = cell.centroid.x;
    let centroid_y = cell.centroid.y;
    for combo in &[
        (centroid_x - *new_height, centroid_y - *new_height),
        (centroid_x + *new_height, centroid_y - *new_height),
        (centroid_x - *new_height, centroid_y + *new_height),
        (centroid_x + *new_height, centroid_y + *new_height),
    ] {
        let new_dist = signed_distance(combo.0, combo.1, polygon);
        mpq.push(Qcell::new(
            combo.0,
            combo.1,
            *new_height,
            new_dist,
            new_dist + *new_height * two.sqrt(),
        ));
    }
}

/// Calculate a Polygon's ideal label position by calculating its pole of inaccessibility
/// The calculation uses an [iterative grid-based algorithm](https://github.com/mapbox/polylabel#how-the-algorithm-works).
pub fn get_core<T>(polygon: &Vec<Point<T>>, tolerance: T) -> Result<Point<T>, PolygonError>
where T: Float + Signed + Bounded + FromPrimitive + Sum + std::fmt::Debug + std::fmt::Display,
{

    let two = T::from_i32(2).unwrap();
    
    // initialize best cell values
    let centroid = get_centroid(polygon);
    let bbox = AABB::from_polygon(polygon);

    log1(&format!("centroid: x,y = {} {}", centroid.x, centroid.y));
    log1(&format!("{:?}", bbox));

    // Ok(Point::new(centroid.x, centroid.y))

    let width = bbox.max.x - bbox.min.x;
    let height = bbox.max.y - bbox.min.y;
    let cell_size = width.min(height);

    // special case for degenerate polygons
    if cell_size == T::zero() {
        return Ok(Point::new(bbox.min.x, bbox.min.y));
    }

    let mut h = cell_size / two;
    let distance = signed_distance(centroid.x, centroid.y, polygon);
    let max_distance = distance + T::zero() * two.sqrt();

    // initialize best cell to centroid
    let mut best_cell = Qcell::new(
        centroid.x,
        centroid.y,
        T::zero(),
        distance,
        max_distance,
    );

    // special case for rectangular polygons
    let bbox_cell_dist = signed_distance(
        bbox.min.x + width / two,
        bbox.min.y + height / two,
        polygon,
    );
    let bbox_cell = Qcell::new(
        bbox.min.x + width / two,
        bbox.min.y + height / two,
        T::zero(),
        bbox_cell_dist,
        bbox_cell_dist + T::zero() * two.sqrt(),
    );

    if bbox_cell.distance > best_cell.distance {
        best_cell = bbox_cell;
    }

    // priority queue
    let mut cell_queue: BinaryHeap<Qcell<T>> = BinaryHeap::new();

    // build an initial quadtree node, which covers the Polygon
    let mut x = bbox.min.x;
    let mut y;
    while x < bbox.max.x {
        y = bbox.min.y;
        while y < bbox.max.y {
            let latest_dist = signed_distance(x + h, y + h, polygon);
            cell_queue.push(Qcell::new(
                x + h,
                y + h,
                h,
                latest_dist,
                latest_dist + h * two.sqrt(),
            ));
            y = y + cell_size;
        }
        x = x + cell_size;
    }

    // try to find better solutions
    while !cell_queue.is_empty() {
        let cell = cell_queue.pop().ok_or_else(|| PolygonError::EmptyQueue)?;
        // Update the best cell if we find a cell with greater distance
        if cell.distance > best_cell.distance {
            best_cell.centroid = Point::new(cell.centroid.x, cell.centroid.y);
            best_cell.extent = cell.extent;
            best_cell.distance = cell.distance;
            best_cell.max_distance = cell.max_distance;
        }
        // Bail out of this iteration if we can't find a better solution
        if cell.max_distance - best_cell.distance <= tolerance {
            continue;
        }
        // Otherwise, add a new quadtree node and start again
        h = cell.extent / two;
        add_quad(&mut cell_queue, &cell, &h, polygon);
    }

    // exhausted the queue, return the best solution we've found
    Ok(Point::new(best_cell.centroid.x, best_cell.centroid.y))
}
