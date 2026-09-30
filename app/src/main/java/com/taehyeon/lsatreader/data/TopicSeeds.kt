package com.taehyeon.lsatreader.data

/** 주제별 시드: Wikipedia 검색어, Guardian (검색어, 섹션) 조합 */
object TopicSeeds {
    val wiki: Map<Topic, List<String>> = mapOf(
        Topic.POLITICS to listOf(
            "separation of powers", "filibuster", "federalism", "gerrymandering", "Electoral College",
            "populism", "Federalist Papers", "parliamentary system", "referendum", "lobbying",
            "Congress of Vienna", "Cuban Missile Crisis", "Marshall Plan", "Treaty of Westphalia",
            "Reconstruction Amendments", "civil service reform", "proportional representation", "deliberative democracy",
        ),
        Topic.ECONOMICS to listOf(
            "Bretton Woods system", "tragedy of the commons", "comparative advantage", "Great Depression",
            "hyperinflation", "Dutch disease", "moral hazard", "tulip mania", "Keynesian economics",
            "behavioral economics", "minimum wage debate", "Smoot–Hawley Tariff Act", "central bank independence",
            "Coase theorem", "principal–agent problem", "gold standard", "Phillips curve", "rent-seeking",
        ),
        Topic.HISTORY to listOf(
            "Printing press", "Black Death", "Silk Road", "Meiji Restoration", "Byzantine Empire",
            "Magna Carta", "Harlem Renaissance", "Library of Alexandria", "Columbian exchange",
            "Hanseatic League", "Venetian Republic", "Industrial Revolution", "abolitionism in the United States",
            "Enlightenment", "Ottoman millet system", "history of cartography", "Joseon", "Scientific Revolution",
        ),
        Topic.TECHNOLOGY to listOf(
            "history of the Internet", "public-key cryptography", "transistor", "Moore's law",
            "semiconductor industry", "machine learning", "open-source software", "Y2K problem",
            "GPS", "containerization", "telegraph", "lithium-ion battery", "network effect",
            "algorithmic bias", "Turing test", "undersea cable", "nuclear power", "right to repair",
        ),
        Topic.SCIENCE to listOf(
            "plate tectonics", "CRISPR", "placebo", "replication crisis", "germ theory of disease",
            "continental drift", "evolution of cooperation", "dark matter", "bird migration",
            "circadian rhythm", "coral bleaching", "antibiotic resistance", "Great Oxidation Event",
            "cognitive dissonance", "linguistic relativity", "island gigantism", "epigenetics", "quantum entanglement",
        ),
        Topic.LAW to listOf(
            "Marbury v. Madison", "judicial review", "stare decisis", "Miranda v. Arizona",
            "Brown v. Board of Education", "originalism", "legal realism", "common law", "tort reform",
            "fair use", "exclusionary rule", "Lochner v. New York", "habeas corpus", "Nuremberg trials",
            "restorative justice", "doctrine of precedent", "strict liability", "jury nullification",
        ),
        Topic.PHILOSOPHY to listOf(
            "trolley problem", "utilitarianism", "social contract", "veil of ignorance", "Ship of Theseus",
            "philosophy of science", "falsifiability", "moral luck", "Stoicism", "epistemology",
            "free will", "virtue ethics", "pragmatism", "Chinese room", "problem of induction",
            "paradigm shift", "natural law", "tragedy of the anticommons",
        ),
        Topic.CULTURE to listOf(
            "Impressionism", "jazz", "Bauhaus", "oral tradition", "museum repatriation",
            "Hollywood studio system", "copyright term extension", "urban planning", "Jane Jacobs",
            "public broadcasting", "language death", "photojournalism", "Harlem Renaissance",
            "architecture of Chicago", "folk music revival", "history of the novel", "Kabuki", "Korean wave",
        ),
    )

    /** (검색어, 섹션). commentisfree = 오피니언/칼럼 */
    val guardian: Map<Topic, List<Pair<String?, String?>>> = mapOf(
        Topic.POLITICS to listOf(null to "politics|us-news|world", "politics" to "commentisfree", "democracy" to "commentisfree"),
        Topic.ECONOMICS to listOf(null to "business", "economy" to "commentisfree", "economics" to "business"),
        Topic.HISTORY to listOf("historians" to null, "history" to "books", "history" to "commentisfree"),
        Topic.TECHNOLOGY to listOf(null to "technology", "technology" to "commentisfree", "artificial intelligence" to null),
        Topic.SCIENCE to listOf(null to "science", "research" to "science|environment", "science" to "commentisfree"),
        Topic.LAW to listOf(null to "law", "supreme court" to null, "law" to "commentisfree"),
        Topic.PHILOSOPHY to listOf("philosophy" to null, "ethics" to "commentisfree", "philosopher" to "books"),
        Topic.CULTURE to listOf(null to "culture", "culture" to "commentisfree", null to "books"),
    )
}
