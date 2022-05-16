/// Handles random sampling from array of ints and probabilities
/// 
/// Interface has single methods of sampling
/// - sample(): return single int
/// 
/// Single sample selects int as discrete probability distribution
/// based on relative rates.
/// 
/// All rates are normalized by sum of all rates
/// 
/// Sampling uses Vose's Alias method:
/// https://www.keithschwarz.com/darts-dice-coins/
/// https://www.keithschwarz.com/interesting/code/?dir=alias-method
/// 

extern crate wasm_bindgen;

use wasm_bindgen::prelude::*;
use rand::prelude::*;
use rand::rngs::SmallRng;
use rand::distributions::weighted::WeightedIndex;

#[wasm_bindgen]
#[derive(Debug)]
pub struct IndexSampler {
    rng: SmallRng,
    dist: WeightedIndex<f64>,
}

#[wasm_bindgen]
impl IndexSampler {

    // Create from input array buffer with format
    // [i1, p_i1, i2, p_i2, ...]
    #[wasm_bindgen(js_name=fromWeights)]
    pub fn from_weights(random_seed: Option<u32>, weights: Vec<f64>) -> Self {
        let rng = if let Some(seed) = random_seed {
            rand::rngs::SmallRng::seed_from_u64(seed as u64)
        } else {
            rand::rngs::SmallRng::from_entropy()
        };

        let dist = WeightedIndex::new(&weights).unwrap();

        IndexSampler {
            rng,
            dist,
        }
    }

    pub fn sample(&mut self) -> usize {
        self.dist.sample(&mut self.rng)
    }
}