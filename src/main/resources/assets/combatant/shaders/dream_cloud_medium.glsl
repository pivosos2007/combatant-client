/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
/* Shared medium for near froxels and distant raymarching.
 * Geometry uses weather-driven Perlin-Worley density, not isolated spheres.
 * Same noise field and extinction model at both levels of detail.
 */
#ifndef DREAM_NOISE_BINDING
#define DREAM_NOISE_BINDING 1
#endif
layout(binding=DREAM_NOISE_BINDING) uniform sampler3D u_CloudNoise;
layout(std430,binding=7) readonly buffer DreamClouds {
    mat4 inverseProjection;
    mat4 inverseView;
    vec4 camera;      // camera-relative periodic origin xyz; time used only for turbulence
    vec4 grid;        // xyz extent; near clip distance
    vec4 outputSize;  // near integration width/height, near max distance
    vec4 shape;       // cloud base height, thickness, coverage, scale
    vec4 appearance;  // density, vanilla night fraction, weather brightness
    vec4 light;       // sun angle, session cloud x/z offset, cirrus enabled
    vec4 wind;        // integrated low X/Z and high X/Z advection; no velocity*time jumps
    vec4 sampling;    // near march steps, frame index, thunder, optional
    vec4 cloudLightColor;
    vec4 cloudShadowColor;
};
const float DREAM_PI=3.14159265358979323846;

vec3 dreamRay(vec2 uv) {
    vec4 q=inverseProjection*vec4(uv*2.-1.,.5,1.);
    return normalize(mat3(inverseView)*(q.xyz/max(abs(q.w),.00001)));
}
vec3 dreamSun() { return normalize(vec3(-sin(light.x),cos(light.x),0.)); }
vec3 dreamLightDir() {return mix(dreamSun(),-dreamSun(),step(.55,appearance.y));}

vec3 dreamFlow(vec3 p){
    float s=max(.4,shape.w);
    vec2 q=p.xz+vec2(light.y,light.z)+wind.xy;
    // Gentle spatially varying shear: large cloud masses remain coherent.
    vec2 shear=vec2(sin(q.y*(DREAM_PI*2./(1024.*s))+p.y*.008),
                    cos(q.x*(DREAM_PI*2./(2048.*s))-p.y*.006))*(7.*s);
    return vec3(q.x+shear.x,p.y,q.y+shear.y);
}
vec3 dreamCirrusFlow(vec3 p){
    float s=max(.4,shape.w);
    vec2 q=p.xz+vec2(light.y,light.z)*.81+wind.zw;
    vec2 bend=vec2(sin(q.y*(DREAM_PI*2./(4096.*s))),
                    cos(q.x*(DREAM_PI*2./(4096.*s))))*(25.*s);
    return vec3(q.x+bend.x,p.y,q.y+bend.y);
}

float dreamWeather(vec2 xz, float scale){
    vec3 p=vec3(xz.x,xz.y,384.);
    return texture(u_CloudNoise,fract(p/(2048.*scale))).a;
}

// Expensive noise is bypassed for empty weather domains and heights.
float dreamDensity(vec3 world,float detailWeight) {
    float scale=max(.4,shape.w);
    vec3 p=dreamFlow(world);
    float weather=dreamWeather(p.xz,scale);
    float cover=clamp(shape.z,0.,1.);
    float field=smoothstep(.62-cover*.27,.77-cover*.23,weather);
    if(field<.004)return 0.;

    float type=texture(u_CloudNoise,fract(vec3(p.xz/(1024.*scale),.36))).a;
    float base=shape.x+shape.y*(type-.5)*.35;
    float height=shape.y*(.65+1.55*smoothstep(.25,.82,weather));
    float h=(world.y-base)/max(1.,height);
    if(h<=0. || h>=1.)return 0.;

    // Puffy tops and broad low bases follow a continuous height profile.
    float vertical=smoothstep(0.,.15,h)*(1.-smoothstep(.62,1.,h));
    float curvedTop=smoothstep(.16,.85,h);
    float threshold=mix(.76,.37,cover)+curvedTop*.15-(weather-.5)*.19;
    vec4 macro=texture(u_CloudNoise,fract(p/(512.*scale)));
    float shapeValue=macro.r*.78+macro.a*.22;
    float body=clamp((shapeValue-threshold)*4.5,0.,1.);
    if(body<.006)return 0.;

    // Erode less in dense cores, more around translucent edges.
    vec4 fine=texture(u_CloudNoise,fract((p+vec3(47.,13.,27.))/(128.*scale)));
    float erosion=mix(.5,fine.g*.63+fine.b*.37,clamp(detailWeight,0.,1.));
    body=clamp(body-(1.-body)*erosion*.55,0.,1.);
    return clamp(body*vertical*field*appearance.x,0.,2.);
}
float dreamDensity(vec3 world) {return dreamDensity(world,1.);}
float dreamShadow(vec3 p,vec3 lightDir,float scale){
    // Short light march through the same physical density field.
    float optical=0.;
    for(int i=0;i<4;i++){
        float t=(float(i)+.75)*shape.y*.45;
        optical+=dreamDensity(p+lightDir*t)*shape.y*.45;
    }
    return exp(-optical*.035);
}
vec3 dreamScattering(vec3 p,vec3 ray,float rho,float shadow){
    vec3 lightDir=dreamLightDir();
    float cosTheta=clamp(dot(ray,lightDir),-1.,1.);
    // Two lobes preserve silver linings without a blown-out analytic halo.
    float forward=pow(max(0.,cosTheta*.5+.5),5.);
    float back=pow(max(0.,-cosTheta*.5+.5),3.);
    float night=clamp(appearance.y,0.,1.);
    float storm=clamp(appearance.z*.62+sampling.z*.80,0.,1.);
    vec3 ambient=mix(cloudShadowColor.rgb*.70,cloudShadowColor.rgb*.09,night);
    vec3 direct=mix(cloudLightColor.rgb*1.10,cloudLightColor.rgb*.20,night);
    ambient*=1.-storm*.78;
    direct*=1.-storm*.88;
    vec3 lit=ambient*(.60+.20*clamp(p.y/max(1.,shape.x+shape.y*2.),0.,1.));
    lit+=direct*shadow*(.38+1.00*forward+.09*back);
    // Dense interiors retain soft ambient bounce but darken substantially.
    return lit*(1.-.22*clamp(rho,0.,1.));
}

float dreamDepthLimit(sampler2D d,vec2 uv,float limit) {
    vec2 delta=.5/vec2(textureSize(d,0));
    // Reversed-Z nearest opaque across footprint.
    float z=max(max(texture(d,uv+vec2(delta.x,delta.y)).r,
                    texture(d,uv+vec2(-delta.x,delta.y)).r),
                max(texture(d,uv+vec2(delta.x,-delta.y)).r,
                    texture(d,uv+vec2(-delta.x,-delta.y)).r));
    if(z<=.000001)return limit;
    vec4 q=inverseProjection*vec4(uv*2.-1.,z,1.);
    return min(limit,length(q.xyz/max(abs(q.w),.00001)));
}
