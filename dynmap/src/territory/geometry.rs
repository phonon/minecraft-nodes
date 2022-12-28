/// geometry.rs
/// ----------------------------------------------------------------
/// 2D geometry primitives

use num_traits::{Bounded, Float, Num};


#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct Point<T> {
    pub x: T,
    pub y: T,
}

impl<T> Point<T> {
    pub fn new(x: T, y: T) -> Point<T> {
        Point {
            x: x,
            y: y
        }
    }
}

impl<T> Point<T> where T: Float {
    // euclidean distance to another point
    pub fn distance_to(&self, p: &Point<T>) -> T {
        let dx = p.x - self.x;
        let dy = p.y - self.y;
        return dx.hypot(dy);
    }
}

// axis aligned bounding box
#[derive(PartialEq, Eq, Clone, Copy, Debug)]
pub struct AABB<T> {
    pub min: Point<T>,
    pub max: Point<T>,
}

impl<T> AABB<T> where T: Num + Copy + Bounded + PartialOrd {
    pub fn new(min: Point<T>, max: Point<T>) -> AABB<T> {
        AABB {
            min: min,
            max: max,
        }
    }
    
    // polygon defined by a point set
    pub fn from_polygon(polygon: &Vec<Point<T>>) -> AABB<T> {
        
        // get bounding box of coords
        let mut xmin = T::max_value();
        let mut xmax = T::min_value();
        let mut ymin = T::max_value();
        let mut ymax = T::min_value();

        for p in polygon.iter() {
            if p.x < xmin { xmin = p.x };
            if p.x > xmax { xmax = p.x };
            if p.y < ymin { ymin = p.y };
            if p.y > ymax { ymax = p.y };
        }
        
        AABB {
            min: Point::new(xmin, ymin),
            max: Point::new(xmax, ymax),
        }
    }

    pub fn from_points(points: impl Iterator<Item=Point<T>>) -> AABB<T> {
        let mut xmin = T::max_value();
        let mut xmax = T::min_value();
        let mut ymin = T::max_value();
        let mut ymax = T::min_value();

        for p in points {
            if p.x < xmin { xmin = p.x };
            if p.x > xmax { xmax = p.x };
            if p.y < ymin { ymin = p.y };
            if p.y > ymax { ymax = p.y };
        }
        
        AABB {
            min: Point::new(xmin, ymin),
            max: Point::new(xmax, ymax),
        }
    }

    pub fn contains(&self, p: &Point<T>) -> bool {
        p.x >= self.min.x && p.x <= self.max.x && 
        p.y >= self.min.y && p.y <= self.max.y
    }

    pub fn contains_xy(&self, x: T, y: T) -> bool {
        x >= self.min.x && x <= self.max.x && 
        y >= self.min.y && y <= self.max.y
    }
}
