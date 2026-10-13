# Continent ellipses — reference for prompt 17

The fixed continent is the union of these 30 upright ellipses. They come from the user's vector drawing (`tools/preview/latitude-series-v2.svg` in the Dunes of Arrakis project), stretched to a 50,000 x 50,000 block square: 31.25 blocks per pixel east-west, 59.24 blocks per pixel north-south, with the drawing's 0,0 mark (pixel 769, 426) as the world origin.

- X is east-west, Z is north-south, **north is negative Z** (as the experiment log records). Confirm that east is positive X before using the table; if it is not, mirror X.
- Half-axes are in blocks: east-west, then north-south. No ellipse is rotated.
- Class is by average diameter: A under 4 km, B 4 to 6.5 km, C 6.5 to 9.5 km, D above.
- Region: ellipses with the same region number share one rock variant. Regions were formed by joining any two ellipses where the overlap covers at least 20% of the smaller one; joins chain.
- The positions do not need to be exact. They mark where land should be.

| Id | Centre X | Centre Z | Half-axis E-W | Half-axis N-S | Class | Region |
| --- | --- | --- | --- | --- | --- | --- |
| 01 | -19381 | -19840 | 803 | 2571 | A | 1 |
| 02 | -13466 | -21428 | 3247 | 3809 | C | 2 |
| 03 | -17397 | -14603 | 2903 | 1902 | B | 3 |
| 04 | -13947 | -15954 | 2284 | 3477 | B | 3 |
| 05 | -7447 | -20687 | 1381 | 2482 | A | 4 |
| 06 | -8125 | -16274 | 513 | 3412 | A | 5 |
| 07 | -6306 | -13709 | 1656 | 693 | A | 6 |
| 08 | -18978 | -9793 | 1984 | 1475 | A | 7 |
| 09 | -15272 | -9289 | 3303 | 3572 | C | 7 |
| 10 | -16462 | -4218 | 2516 | 2885 | B | 8 |
| 11 | -13231 | -3928 | 2466 | 2150 | B | 8 |
| 12 | -12547 | 1866 | 2138 | 4094 | B | 9 |
| 13 | -9784 | 1641 | 1878 | 5865 | C | 9 |
| 14 | -7087 | -1961 | 2697 | 3134 | B | 9 |
| 15 | -2113 | -1600 | 3953 | 2014 | B | 10 |
| 16 | -4578 | 4573 | 4744 | 4473 | C | 11 |
| 17 | -1369 | 3122 | 4581 | 3827 | C | 11 |
| 18 | 4416 | 6339 | 3094 | 2216 | B | 12 |
| 19 | 7391 | 3323 | 3306 | 2642 | B | 13 |
| 20 | 11178 | 1931 | 3128 | 3264 | B | 13 |
| 21 | 14591 | 492 | 1416 | 4710 | B | 14 |
| 22 | 15700 | -4354 | 3069 | 2755 | B | 14 |
| 23 | 17103 | -9153 | 4475 | 4556 | C | 14 |
| 24 | 17222 | -17222 | 7706 | 5954 | D | 15 |
| 25 | 8734 | -14757 | 1347 | 4254 | B | 16 |
| 26 | 6847 | -10711 | 4203 | 2393 | B | 16 |
| 27 | 7484 | -5758 | 1394 | 3531 | B | 17 |
| 28 | 1831 | -9502 | 2703 | 2393 | B | 18 |
| 29 | -1934 | -8809 | 2716 | 2150 | B | 19 |
| 30 | -3316 | -5160 | 2891 | 2707 | B | 20 |

## Regions

- Region 1: ellipse 01
- Region 2: ellipse 02
- Region 3: ellipses 03, 04
- Region 4: ellipse 05
- Region 5: ellipse 06
- Region 6: ellipse 07
- Region 7: ellipses 08, 09
- Region 8: ellipses 10, 11
- Region 9: ellipses 12, 13, 14
- Region 10: ellipse 15
- Region 11: ellipses 16, 17
- Region 12: ellipse 18
- Region 13: ellipses 19, 20
- Region 14: ellipses 21, 22, 23
- Region 15: ellipse 24
- Region 16: ellipses 25, 26
- Region 17: ellipse 27
- Region 18: ellipse 28
- Region 19: ellipse 29
- Region 20: ellipse 30

20 regions in total.

## Overlapping pairs

Share of the smaller ellipse that lies inside the other. Pairs at 20% or more are in the same region; every other pair listed here is a seam between two regions.

| Pair | Overlap | Same region |
| --- | --- | --- |
| 16 and 17 | 57% | yes |
| 08 and 09 | 33% | yes |
| 22 and 23 | 33% | yes |
| 10 and 11 | 27% | yes |
| 19 and 20 | 27% | yes |
| 25 and 26 | 22% | yes |
| 12 and 13 | 21% | yes |
| 13 and 14 | 21% | yes |
| 03 and 04 | 20% | yes |
| 21 and 22 | 20% | yes |
| 23 and 24 | 19% | no |
| 28 and 29 | 19% | no |
| 02 and 04 | 17% | no |
| 26 and 28 | 17% | no |
| 20 and 21 | 16% | no |
| 15 and 17 | 15% | no |
| 14 and 15 | 14% | no |
| 18 and 19 | 14% | no |
| 13 and 16 | 13% | no |
| 29 and 30 | 13% | no |
| 09 and 10 | 12% | no |
| 15 and 30 | 12% | no |
| 05 and 06 | 11% | no |
| 26 and 27 | 8% | no |
| 14 and 30 | 6% | no |
| 14 and 16 | 4% | no |
| 17 and 18 | 4% | no |
| 24 and 25 | 4% | no |
| 06 and 07 | 2% | no |
| 11 and 12 | 2% | no |
| 04 and 09 | 1% | no |

## As JSON

```json
[
  {"id": 1, "x": -19381, "z": -19840, "halfEW": 803, "halfNS": 2571, "class": "A", "region": 1},
  {"id": 2, "x": -13466, "z": -21428, "halfEW": 3247, "halfNS": 3809, "class": "C", "region": 2},
  {"id": 3, "x": -17397, "z": -14603, "halfEW": 2903, "halfNS": 1902, "class": "B", "region": 3},
  {"id": 4, "x": -13947, "z": -15954, "halfEW": 2284, "halfNS": 3477, "class": "B", "region": 3},
  {"id": 5, "x": -7447, "z": -20687, "halfEW": 1381, "halfNS": 2482, "class": "A", "region": 4},
  {"id": 6, "x": -8125, "z": -16274, "halfEW": 513, "halfNS": 3412, "class": "A", "region": 5},
  {"id": 7, "x": -6306, "z": -13709, "halfEW": 1656, "halfNS": 693, "class": "A", "region": 6},
  {"id": 8, "x": -18978, "z": -9793, "halfEW": 1984, "halfNS": 1475, "class": "A", "region": 7},
  {"id": 9, "x": -15272, "z": -9289, "halfEW": 3303, "halfNS": 3572, "class": "C", "region": 7},
  {"id": 10, "x": -16462, "z": -4218, "halfEW": 2516, "halfNS": 2885, "class": "B", "region": 8},
  {"id": 11, "x": -13231, "z": -3928, "halfEW": 2466, "halfNS": 2150, "class": "B", "region": 8},
  {"id": 12, "x": -12547, "z": 1866, "halfEW": 2138, "halfNS": 4094, "class": "B", "region": 9},
  {"id": 13, "x": -9784, "z": 1641, "halfEW": 1878, "halfNS": 5865, "class": "C", "region": 9},
  {"id": 14, "x": -7087, "z": -1961, "halfEW": 2697, "halfNS": 3134, "class": "B", "region": 9},
  {"id": 15, "x": -2113, "z": -1600, "halfEW": 3953, "halfNS": 2014, "class": "B", "region": 10},
  {"id": 16, "x": -4578, "z": 4573, "halfEW": 4744, "halfNS": 4473, "class": "C", "region": 11},
  {"id": 17, "x": -1369, "z": 3122, "halfEW": 4581, "halfNS": 3827, "class": "C", "region": 11},
  {"id": 18, "x": 4416, "z": 6339, "halfEW": 3094, "halfNS": 2216, "class": "B", "region": 12},
  {"id": 19, "x": 7391, "z": 3323, "halfEW": 3306, "halfNS": 2642, "class": "B", "region": 13},
  {"id": 20, "x": 11178, "z": 1931, "halfEW": 3128, "halfNS": 3264, "class": "B", "region": 13},
  {"id": 21, "x": 14591, "z": 492, "halfEW": 1416, "halfNS": 4710, "class": "B", "region": 14},
  {"id": 22, "x": 15700, "z": -4354, "halfEW": 3069, "halfNS": 2755, "class": "B", "region": 14},
  {"id": 23, "x": 17103, "z": -9153, "halfEW": 4475, "halfNS": 4556, "class": "C", "region": 14},
  {"id": 24, "x": 17222, "z": -17222, "halfEW": 7706, "halfNS": 5954, "class": "D", "region": 15},
  {"id": 25, "x": 8734, "z": -14757, "halfEW": 1347, "halfNS": 4254, "class": "B", "region": 16},
  {"id": 26, "x": 6847, "z": -10711, "halfEW": 4203, "halfNS": 2393, "class": "B", "region": 16},
  {"id": 27, "x": 7484, "z": -5758, "halfEW": 1394, "halfNS": 3531, "class": "B", "region": 17},
  {"id": 28, "x": 1831, "z": -9502, "halfEW": 2703, "halfNS": 2393, "class": "B", "region": 18},
  {"id": 29, "x": -1934, "z": -8809, "halfEW": 2716, "halfNS": 2150, "class": "B", "region": 19},
  {"id": 30, "x": -3316, "z": -5160, "halfEW": 2891, "halfNS": 2707, "class": "B", "region": 20}
]
```
