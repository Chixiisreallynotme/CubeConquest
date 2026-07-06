# Loop Plan: Improve Cube Textures

## Objective
Update the block textures (blue_cube and red_cube) to look like wooden crates with wooden planks, while keeping their respective red and blue colors. 

## Context
The user wants better quality textures for the game blocks. We need to modify the texture generation logic (or create a new script) to produce high-quality pixel art wooden crates instead of metal crates.

## Steps
1. Update or create a `TextureGenerator` Java class that generates a 16x16 texture of a wooden crate.
2. The wooden crate should have vertical or horizontal planks, wood grain noise, dark plank separations, an outer frame, and a cross brace if applicable.
3. Apply the base color (Red or Blue) to the wood so that it clearly identifies the team.
4. Compile and run the generator to output `red_cube.png` and `blue_cube.png` into `src/main/resources/assets/cubeconquest/textures/block/`.
5. Verify the files exist and have the correct dimensions.

## Stop Condition
The loop should stop when `red_cube.png` and `blue_cube.png` have been successfully generated with a colored wooden crate aesthetic, and the changes are ready for the user to review.
