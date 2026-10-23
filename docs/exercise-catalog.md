# Offline exercise catalog

The bundled offline catalog contains **116 exercises** (16 original plus 100 additions) at library revision 2. Every new exercise requires zero equipment (`equipment: []`). The JSON asset is packaged by the Android build into `assets/relven/exercise-library-v1.json`; it is available without a network connection. Swedish and English names and instruction steps are stored side by side for every entry. The schema remains version 1.

The first 16 records, their UUIDs, revisions, names, and instructions are preserved exactly. Every addition has a stable UUIDv4 and starts at exercise revision 1. Increment the exercise revision if that exercise’s meaning or instructions change, retaining its ID; increment the library revision when the bundled catalog changes. Workout v3 snapshots remain immutable and resolve against their own exercise revision/content even when the installed library revision advances. Custom exercises remain in their separate import/export collection and cannot collide with bundled IDs.

Categories remain `lower_body`, `upper_body`, `core`, and `warmup_mobility`; the existing muscle-group enum has no dedicated adductor or tibialis-anterior values, so the nearest valid groups are used with specific `movementPattern` keys. The schema has no progression, risk, or contraindication fields. Progression relationships below are editorial guidance, not medical advice or a prescription. All exercises use only bodyweight, floor, or a fixed wall; there are no accessories, external media, or equipment-dependent movements. The catalog does not claim to prevent or treat injury. For ongoing symptoms or prior shoulder dislocation, exercise selection and progression should be guided by an appropriately qualified clinician.

## Progression families

- **Hamstrings/posterior chain:** bridge activation → bilateral and unilateral bridge strength → long-lever holds and bridge walkouts → controlled single-leg hinge/eccentric loading.
- **Groin/hip:** low-load adductor isometrics → side-lying and standing adduction → controlled lateral shifts/lunges and hip stabilization.
- **Lower leg:** ankle/toe mobility and balance → dorsiflexion, straight/bent-knee calf capacity, and controlled heel lowering → low-impact landing preparation; hopping entries are explicitly higher impact.
- **Shoulder:** scapular awareness and wall mobility → conservative wall/self-resisted isometrics → closed-chain and graduated push-up loading. Keep all work in a comfortable range.
- **Core:** basic anti-extension and trunk holds → unilateral, cross-body and anti-rotation control → coordinated athletic stance and movement.
- **Athleticism:** deceleration/landing positions → low-amplitude bilateral hops → controlled unilateral or lateral landing. Skip impact work when shin symptoms are active.

## New exercise inventory

| Focus area | Svenska | English |
|---|---|---|
| Hamstrings | Höftlyft med marsch | Glute bridge march |
| Hamstrings | Enbenshöftlyft | Single-leg glute bridge |
| Hamstrings | Höftlyft med lång hävarm | Long-lever bridge |
| Hamstrings | Långt höftlyft med håll | Long-lever bridge hold |
| Hamstrings | Höftlyft med utgång | Bridge walkout |
| Hamstrings | Enbens höftlyft med hälgång | Single-leg bridge walkout |
| Hamstrings | Hälpress mot golvet | Supine heel press |
| Hamstrings | Magliggande häldrag | Prone heel drag |
| Hamstrings | Enbenshöftfällning med räckning | Single-leg hinge reach |
| Hamstrings | Höftfällning med stödben | Kickstand hip hinge |
| Hamstrings | Stående höftsträckning | Standing hip extension |
| Hamstrings | Fyrfota höftsträckning | Quadruped hip extension |
| Groin | Sidliggande adduktorlyft | Side-lying hip adduction |
| Groin | Böjt adduktorlyft | Bent-knee hip adduction |
| Groin | Självpress för adduktorer | Self-resisted hip adduction |
| Groin | Stående adduktionspress | Standing self-resisted adduction |
| Groin | Sidoutfall med viktförskjutning | Lateral weight shift |
| Groin | Adduktorrockning | Adductor rock-back |
| Groin | Bred knärockning | Wide-knee rock-back |
| Groin | 90/90 inåtrotation | 90/90 hip internal rotation |
| Groin | Sidliggande höftrotation | Side-lying hip rotation |
| Groin | Sidosteg i djup position | Low lateral shuffle step |
| Groin | Enbens höftabduktion | Single-leg hip abduction |
| Groin | Utfall med sidolutning | Lateral lunge with reach |
| Lower leg | Tibialislyft mot vägg | Wall tibialis raise |
| Lower leg | Sittande tålyft | Seated toe raise |
| Lower leg | Självmotstånd för fotlyft | Self-resisted dorsiflexion |
| Lower leg | Tåhävning med böjt knä | Bent-knee calf raise |
| Lower leg | Enbens tåhävning | Single-leg calf raise |
| Lower leg | Enbens tåhävning med böjt knä | Single-leg bent-knee calf raise |
| Lower leg | Tåhävningshåll | Calf raise hold |
| Lower leg | Excentrisk hälsänkning | Eccentric heel lowering |
| Lower leg | Tåyoga | Toe yoga |
| Lower leg | Kortfot | Short-foot exercise |
| Lower leg | Tåspridning | Toe spreading |
| Lower leg | Enbens fotledsbalans | Single-leg ankle balance |
| Lower leg | Fotledspress inåt och utåt | Self-resisted ankle inversion and eversion |
| Lower leg | Knä fram över tå | Knee-to-toe ankle mobility |
| Functional lower body | Statisk split squat | Split squat |
| Functional lower body | Framåtutfall | Forward lunge |
| Functional lower body | Sidoutfall | Lateral lunge |
| Functional lower body | Split squat-håll | Split squat isometric hold |
| Functional lower body | Enbensknäböj, grund | Shallow single-leg squat |
| Functional lower body | Knäböj med sidräckning | Single-leg squat with reach |
| Functional lower body | Bred knäböj | Wide-stance squat |
| Functional lower body | Smal knäböj | Narrow-stance squat |
| Functional lower body | Bakåtutfall med knälyft | Reverse lunge to knee drive |
| Functional lower body | Sidoutfall till balans | Lateral lunge to balance |
| Functional lower body | Utfallssteg bakåt | Step-back lunge |
| Functional lower body | Utfall bakåt med pendel | Reverse lunge with hip hinge |
| Functional lower body | Väggsittning med tålyft | Wall sit with toe lift |
| Functional lower body | Knäböj med tåhävning | Squat to calf raise |
| Functional lower body | Växlande sidoutfall | Alternating lateral lunge |
| Functional lower body | Enbens knäböj till tå | Single-leg squat and reach |
| Core | Planka med axelklapp | Plank shoulder tap |
| Core | Underarmsplanka med knälyft | Forearm plank knee lift |
| Core | Lång planka | Long-lever plank |
| Core | Dead bug med hältryck | Dead bug heel tap |
| Core | Dead bug med bensträck | Dead bug leg extension |
| Core | Hollow tuck-håll | Hollow tuck hold |
| Core | Hollow body-håll | Hollow body hold |
| Core | Omvänd magcurl | Reverse crunch |
| Core | Sidoplanka på knä | Kneeling side plank |
| Core | Sidoplanka med benlyft | Side plank with leg lift |
| Core | Sidoplanka med rotation | Side plank thread-through |
| Core | Fågelhund med knädrag | Bird dog knee drive |
| Core | Björnhåll | Bear hover hold |
| Core | Björngång | Bear crawl |
| Shoulder | Skulderbladspush-up | Scapular push-up |
| Shoulder | Väggglidning | Wall slide |
| Shoulder | Väggängel | Wall angel |
| Shoulder | Väggpress med skulderblad | Wall push-up plus |
| Shoulder | Väggarmhävning | Wall push-up |
| Shoulder | Väggpress med viktförskjutning | Wall plank weight shift |
| Shoulder | Magliggande W-lyft | Prone W lift |
| Shoulder | Magliggande Y-lyft | Prone Y lift |
| Shoulder | Magliggande T-lyft | Prone T lift |
| Shoulder | Självmotstånd utåtrotation | Self-resisted shoulder external rotation |
| Shoulder | Självmotstånd inåtrotation | Self-resisted shoulder internal rotation |
| Shoulder | Inåtrotation mot vägg | Wall internal-rotation isometric |
| Shoulder | Axelböjning mot vägg | Wall shoulder-flexion isometric |
| Shoulder | Serratusreach på rygg | Supine serratus reach |
| Plyometrics | Landning till atletisk position | Snap-down landing |
| Plyometrics | Knäböjshopp med landning | Squat jump and stick |
| Plyometrics | Små tåstudsar | Low pogo hops |
| Plyometrics | Enbens tåstudsar | Single-leg pogo hops |
| Plyometrics | Små hopp fram och tillbaka | Low forward-back hops |
| Plyometrics | Små sidledshopp | Low lateral line hops |
| Plyometrics | Skridskosteg | Skater step |
| Plyometrics | Skridskohopp med stopp | Skater hop and stick |
| Plyometrics | Sprintposition mot vägg | Wall sprint-drive |
| Plyometrics | Tre steg och kontrollerat stopp | Three-step acceleration and stop |
| Mobility | Bensving fram och bak | Front-to-back leg swing |
| Mobility | Bensving i sidled | Side-to-side leg swing |
| Mobility | Höftgrindar | Hip open-and-close gates |
| Mobility | Gående hamstringsvep | Walking hamstring sweep |
| Mobility | Fotledscirklar | Ankle circles |
| Mobility | Bröstryggsrotation på sidan | Side-lying thoracic rotation |
| Mobility | Stående bröstryggsrotation | Standing thoracic rotation |
| Mobility | Arm- och skuldercirklar | Arm and shoulder circles |

## Custom collection files

Users can export only their custom exercises to a UTF-8 JSON collection (`relven.exercise-collection`, schema version 1; see [`exercise-collection-v1.schema.json`](../schemas/exercise-collection-v1.schema.json)) or merge one into the local catalog. Bundled catalog entries are never exported or edited. Each custom record retains its UUID, revision, archive state, bilingual names and instructions, category, movement pattern, muscle groups, and equipment. The format has no media or network references.

Imports are limited to 1 MiB and 200 records, and the merged custom catalog remains limited to 200 records. New IDs are added; exact existing records are ignored; duplicate IDs in the file, IDs colliding with the bundled library, or any changed record with an existing ID reject the complete import before the atomic write. Import never deletes local exercises.

The JSON Schema describes field types and bounds. Runtime validation also rejects whitespace-only required text, surrounding whitespace in imported text, duplicate equipment entries, and duplicate exercise IDs. Catalog schema validation and Android offline loading/search/snapshot behavior are covered by automated validation and tests.
