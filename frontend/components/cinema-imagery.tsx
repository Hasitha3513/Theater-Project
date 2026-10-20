"use client";
import Image from "next/image";
import { useState } from "react";
import { Button } from "./primitives";
export const cinemaAssets = {
	hero: {
		label: "Private cinema",
		alt: "AI-generated concept of an empty navy private cinema with leather recliners, violet ceiling light and amber aisle lighting.",
	},
	violet: {
		label: "Violet lighting",
		alt: "AI-generated concept of an empty luxury screening room with violet cove lighting and dark leather seating.",
	},
	blue: {
		label: "Blue lighting",
		alt: "AI-generated concept of an empty private cinema with blue ceiling lighting and dark recliners.",
	},
	amber: {
		label: "Warm amber",
		alt: "AI-generated concept of an empty wood-panelled luxury cinema with warm amber lighting and charcoal seating.",
	},
	recliners: {
		label: "Recliner seating",
		alt: "AI-generated concept showing black leather recliners in a modern private cinema with violet and gold accents.",
	},
} as const;
export type CinemaAsset = keyof typeof cinemaAssets;
export function CinemaIllustration({
	asset = "violet",
	hero = false,
	roomFallback = false,
}: {
	asset?: CinemaAsset;
	hero?: boolean;
	roomFallback?: boolean;
}) {
	const [failed, setFailed] = useState(false);
	const item = cinemaAssets[asset];
	return (
		<figure
			className={`cinema-illustration${hero ? " cinema-illustration-hero" : ""}`}
		>
			{!failed ? (
				<picture>
					<source
						type="image/webp"
						srcSet={[480, 960, 1672]
							.map((width) => `/cinema/${asset}-${width}.webp ${width}w`)
							.join(", ")}
						sizes={
							hero
								? "(min-width: 1024px) 50vw, 100vw"
								: "(min-width: 1024px) 66vw, 100vw"
						}
					/>
					<Image
						unoptimized
						width={1672}
						height={941}
						src={`/cinema/${asset}-960.webp`}
						alt={item.alt}
						loading={hero ? "eager" : "lazy"}
						fetchPriority={hero ? "high" : "auto"}
						decoding="async"
						onError={() => setFailed(true)}
					/>
				</picture>
			) : (
				<p className="cinema-image-fallback" role="status">
					Cinema illustration unavailable
				</p>
			)}
			<figcaption>
				{roomFallback && <span>Room photograph unavailable · </span>}
				AI-generated illustration · Not a photograph of this venue or room.
			</figcaption>
		</figure>
	);
}
export function CinemaConceptGallery() {
	const [selected, setSelected] = useState<CinemaAsset>("violet");
	return (
		<div className="cinema-concept-gallery">
			<CinemaIllustration key={selected} asset={selected} roomFallback />
			<fieldset className="cinema-concept-controls">
				<legend>Illustrative cinema concepts</legend>
				{(["violet", "blue", "amber", "recliners"] as const).map((asset) => (
					<Button
						key={asset}
						variant="secondary"
						aria-pressed={selected === asset}
						onClick={() => setSelected(asset)}
					>
						{cinemaAssets[asset].label}
					</Button>
				))}
			</fieldset>
		</div>
	);
}
