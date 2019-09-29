use crate::Genotype;

pub fn crossover<R: rand::Rng>(rng: &mut R, gen1: &Genotype, gen2: &Genotype) -> Genotype {
    let mut gen1c = gen1.clone();
    let mut gen2c = gen2.clone();
    gen1c.0.sort_by_key(|n| n.task);
    gen2c.0.sort_by_key(|n| n.task);
    let mut geno3 = Genotype(vec![]);
    for (n1, n2) in gen1.0.iter().zip(gen2.0.iter()) {
        // Simplest possible crossover: pick left or right.
        if rng.gen_bool(0.5) {
            println!("Taking from left");
            geno3.push(n1.clone())
        } else {
            println!("Taking from right");
            geno3.push(n2.clone())
        }
    }

    geno3
}

#[cfg(test)]
mod tests {
    use crate::crossover::crossover;
    use crate::{Project, Schedule};

    static PRJ: &str = include_str!("sample.json");

    #[test]
    fn can_crossover() {
        let prj: Project = serde_json::from_str(PRJ).unwrap();
        let schedule1 = Schedule::new_greedy(prj.clone());
        let schedule2 = Schedule::new_greedy(prj);
        // Sanity check: schedule1 and schedule2 have different genotypes.
        assert_ne!(schedule1.genotype, schedule2.genotype, "Sanity check");
        let mut rng = rand::thread_rng();
        let geno3 = crossover(&mut rng, &schedule1.genotype, &schedule2.genotype);
        // Geno3 should not be identical to either parent.
        assert_ne!(schedule1.genotype, geno3);
        assert_ne!(schedule2.genotype, geno3);
    }
}
