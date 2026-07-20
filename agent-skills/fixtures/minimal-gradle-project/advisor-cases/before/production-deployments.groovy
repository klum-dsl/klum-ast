package onboarding

Deployment catalog = Deployment.Create.With('catalog') {
    environment 'production'
    service {
        image 'catalog:1.0'
    }
}

Deployment billing = Deployment.Create.With('billing') {
    environment 'production'
    service {
        image 'catalog:1.0'
    }
}
