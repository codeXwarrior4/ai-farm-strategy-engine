import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  createFarmerProfile,
  getFarmerProfile,
  updateFarmerProfile,
  type FarmerProfileResponse,
  type CreateFarmerProfileRequest,
  type UpdateFarmerProfileRequest,
  type SoilType,
  type IrrigationSource,
  type RiskTolerance,
} from '../api/farmerProfile';
import { ApiError, getAuthToken, setAuthToken } from '../api/apiClient';

type FormState = 'idle' | 'submitting' | 'success' | 'error';

interface FormErrors {
  [field: string]: string;
}

interface ProfileFormData {
  fullName: string;
  phoneNumber: string;
  primaryState: string;
  primaryDistrict: string;
  totalLandHectares: string;
  yearsOfExperience: number;
  preferredLanguage: string;
  cultivableHectares: string;
  soilType: SoilType | '';
  irrigationSource: IrrigationSource | '';
  waterAvailabilityMonths: string;
  workingCapitalInr: string;
  riskTolerance: RiskTolerance | '';
}

const initialFormData: ProfileFormData = {
  fullName: '',
  phoneNumber: '',
  primaryState: '',
  primaryDistrict: '',
  totalLandHectares: '',
  yearsOfExperience: 0,
  preferredLanguage: 'en',
  cultivableHectares: '',
  soilType: '',
  irrigationSource: '',
  waterAvailabilityMonths: '',
  workingCapitalInr: '',
  riskTolerance: '',
};

export default function FarmerProfilePage() {
  const [userId, setUserId] = useState('');
  const [token, setToken] = useState(() => getAuthToken() || '');
  const [isExistingProfile, setIsExistingProfile] = useState(false);
  const [profileData, setProfileData] = useState<FarmerProfileResponse | null>(null);

  const [form, setForm] = useState<ProfileFormData>(initialFormData);
  const [formState, setFormState] = useState<FormState>('idle');
  const [fieldErrors, setFieldErrors] = useState<FormErrors>({});
  const [globalError, setGlobalError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const [fetchState, setFetchState] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [fetchError, setFetchError] = useState('');

  // Synchronize token changes with apiClient
  useEffect(() => {
    setAuthToken(token.trim() || null);
  }, [token]);

  function populateFormFromProfile(profile: FarmerProfileResponse) {
    setForm({
      fullName: profile.fullName || '',
      phoneNumber: profile.phoneNumber || '',
      primaryState: profile.primaryState || '',
      primaryDistrict: profile.primaryDistrict || '',
      totalLandHectares: profile.totalLandHectares !== null && profile.totalLandHectares !== undefined
        ? profile.totalLandHectares.toString()
        : '',
      yearsOfExperience: profile.yearsOfExperience ?? 0,
      preferredLanguage: profile.preferredLanguage || 'en',
      cultivableHectares: profile.cultivableHectares !== null && profile.cultivableHectares !== undefined
        ? profile.cultivableHectares.toString()
        : '',
      soilType: profile.soilType || '',
      irrigationSource: profile.irrigationSource || '',
      waterAvailabilityMonths: profile.waterAvailabilityMonths !== null && profile.waterAvailabilityMonths !== undefined
        ? profile.waterAvailabilityMonths.toString()
        : '',
      workingCapitalInr: profile.workingCapitalInr !== null && profile.workingCapitalInr !== undefined
        ? profile.workingCapitalInr.toString()
        : '',
      riskTolerance: profile.riskTolerance || '',
    });
  }

  function handleInputChange(
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) {
    const { name, value } = e.target;
    setForm(prev => ({
      ...prev,
      [name]: name === 'yearsOfExperience' ? (parseInt(value, 10) || 0) : value,
    }));

    // Clear field-level error on edit
    if (fieldErrors[name]) {
      setFieldErrors(prev => {
        const next = { ...prev };
        delete next[name];
        return next;
      });
    }
  }

  function validateClientSide(): boolean {
    const errors: FormErrors = {};

    if (!form.fullName.trim()) {
      errors.fullName = 'Full name is required';
    } else if (form.fullName.trim().length < 2 || form.fullName.trim().length > 200) {
      errors.fullName = 'Full name must be between 2 and 200 characters';
    }

    if (form.yearsOfExperience < 0 || form.yearsOfExperience > 99) {
      errors.yearsOfExperience = 'Years of experience must be between 0 and 99';
    }

    const totalLand = form.totalLandHectares.trim() ? parseFloat(form.totalLandHectares) : undefined;
    const cultivable = form.cultivableHectares.trim() ? parseFloat(form.cultivableHectares) : undefined;

    if (totalLand !== undefined && (isNaN(totalLand) || totalLand <= 0)) {
      errors.totalLandHectares = 'Total land must be greater than 0 if provided';
    }

    if (cultivable !== undefined) {
      if (isNaN(cultivable) || cultivable <= 0) {
        errors.cultivableHectares = 'Cultivable hectares must be greater than 0 if provided';
      } else if (totalLand !== undefined && cultivable > totalLand) {
        errors.cultivableHectares = 'Cultivable hectares cannot exceed total land hectares';
      } else if (totalLand === undefined && !isExistingProfile) {
        errors.cultivableHectares = 'Total land must be specified when cultivable hectares is provided';
      }
    }

    if (form.waterAvailabilityMonths.trim()) {
      const months = parseInt(form.waterAvailabilityMonths, 10);
      if (isNaN(months) || months < 0 || months > 12) {
        errors.waterAvailabilityMonths = 'Water availability months must be between 0 and 12';
      }
    }

    if (form.workingCapitalInr.trim()) {
      const capital = parseFloat(form.workingCapitalInr);
      if (isNaN(capital) || capital < 0) {
        errors.workingCapitalInr = 'Working capital cannot be negative';
      }
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setGlobalError('Please fix the validation errors before submitting.');
      return false;
    }

    return true;
  }

  async function handleFetchProfile() {
    const parsedUserId = parseInt(userId.trim(), 10);
    if (isNaN(parsedUserId) || parsedUserId <= 0) {
      setFetchError('Please enter a valid numeric User ID.');
      return;
    }

    setFetchState('loading');
    setFetchError('');
    setGlobalError('');

    try {
      const profile = await getFarmerProfile(parsedUserId);
      setProfileData(profile);
      populateFormFromProfile(profile);
      setIsExistingProfile(true);
      setFetchState('success');
      setSuccessMessage(`Loaded existing profile for "${profile.fullName}" (Profile ID: ${profile.id}). You can update fields below.`);
    } catch (error) {
      setFetchState('error');
      if (error instanceof ApiError) {
        if (error.status === 404) {
          setIsExistingProfile(false);
          setProfileData(null);
          setFetchError(`No farmer profile exists for User ID ${parsedUserId}. Fill the form below to create one.`);
        } else if (error.status === 401) {
          setFetchError('Authentication required (401). Please supply a valid JWT token.');
        } else if (error.status === 403) {
          setFetchError('Authorization failed (403). The token does not match the requested User ID.');
        } else {
          setFetchError(`${error.status}: ${error.data.message}`);
        }
      } else if (error instanceof Error) {
        setFetchError(error.message);
      } else {
        setFetchError('An unexpected error occurred while fetching the profile.');
      }
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const parsedUserId = parseInt(userId.trim(), 10);
    if (isNaN(parsedUserId) || parsedUserId <= 0) {
      setGlobalError('Please enter a valid User ID before submitting.');
      return;
    }

    if (!validateClientSide()) {
      return;
    }

    setFormState('submitting');
    setFieldErrors({});
    setGlobalError('');
    setSuccessMessage('');

    const payload: CreateFarmerProfileRequest | UpdateFarmerProfileRequest = {
      fullName: form.fullName.trim(),
      phoneNumber: form.phoneNumber.trim() || undefined,
      primaryState: form.primaryState.trim() || undefined,
      primaryDistrict: form.primaryDistrict.trim() || undefined,
      totalLandHectares: form.totalLandHectares.trim() ? parseFloat(form.totalLandHectares) : undefined,
      yearsOfExperience: form.yearsOfExperience,
      preferredLanguage: form.preferredLanguage.trim() || 'en',
      cultivableHectares: form.cultivableHectares.trim() ? parseFloat(form.cultivableHectares) : undefined,
      soilType: form.soilType ? form.soilType : undefined,
      irrigationSource: form.irrigationSource ? form.irrigationSource : undefined,
      waterAvailabilityMonths: form.waterAvailabilityMonths.trim() ? parseInt(form.waterAvailabilityMonths, 10) : undefined,
      workingCapitalInr: form.workingCapitalInr.trim() ? parseFloat(form.workingCapitalInr) : undefined,
      riskTolerance: form.riskTolerance ? form.riskTolerance : undefined,
    };

    try {
      let saved: FarmerProfileResponse;
      if (isExistingProfile) {
        saved = await updateFarmerProfile(parsedUserId, payload);
        setSuccessMessage(`✓ Profile successfully updated! (ID: ${saved.id})`);
      } else {
        saved = await createFarmerProfile(parsedUserId, payload);
        setSuccessMessage(`✓ Profile successfully created! (ID: ${saved.id})`);
        setIsExistingProfile(true);
      }

      setProfileData(saved);
      populateFormFromProfile(saved);
      setFormState('success');
    } catch (error) {
      setFormState('error');
      if (error instanceof ApiError) {
        if (error.data.fieldErrors) {
          setFieldErrors(error.data.fieldErrors);
        }
        if (error.status === 401) {
          setGlobalError('Authentication failed (401). Please verify your JWT token.');
        } else if (error.status === 403) {
          setGlobalError('Access denied (403). Your token does not permit modifying this user profile.');
        } else if (error.status === 409) {
          setGlobalError('Conflict (409). A profile already exists for this user. Click "Fetch Profile" to load and edit it.');
          setIsExistingProfile(true);
        } else {
          setGlobalError(error.data.message || 'Validation failed. Please inspect the highlighted fields.');
        }
      } else if (error instanceof Error) {
        setGlobalError(error.message);
      } else {
        setGlobalError('An unexpected error occurred during submission.');
      }
    }
  }

  function handleResetForm() {
    setForm(initialFormData);
    setIsExistingProfile(false);
    setProfileData(null);
    setFieldErrors({});
    setGlobalError('');
    setSuccessMessage('');
    setFetchError('');
  }

  const isSubmitting = formState === 'submitting';

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 overflow-hidden">
      {/* Background ambient lighting */}
      <div className="fixed inset-0 pointer-events-none" aria-hidden="true">
        <div className="absolute top-[-20%] right-[-10%] w-[550px] h-[550px] bg-indigo-600/15 rounded-full blur-3xl" />
        <div className="absolute bottom-[-20%] left-[-10%] w-[450px] h-[450px] bg-emerald-600/10 rounded-full blur-3xl" />
      </div>

      <div className="relative max-w-3xl mx-auto px-4 sm:px-6 py-10">
        {/* Navigation */}
        <div className="flex items-center justify-between mb-6">
          <Link
            to="/"
            className="inline-flex items-center gap-2 text-slate-400 hover:text-slate-200 text-sm transition-colors"
          >
            ← Back to Status
          </Link>
          <span className="text-xs px-2.5 py-1 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 font-mono">
            Phase 2A · Farm Data Foundation
          </span>
        </div>

        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl sm:text-4xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 via-indigo-300 to-purple-400">
            Farmer Profile & Farm Context
          </h1>
          <p className="text-slate-400 text-sm mt-2">
            Configure identity, land scale, soil classification, water access, and financial constraints for farm strategy recommendations.
          </p>
        </div>

        {/* Section 0: User Authentication & Context */}
        <section className="glass-card p-5 sm:p-6 mb-6" aria-labelledby="auth-context-heading">
          <h2 id="auth-context-heading" className="text-base font-semibold text-slate-200 mb-3 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-indigo-400"></span>
            1. User Identity & Authentication
          </h2>

          <div className="grid sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="userId-input" className="form-label">
                User ID <span className="text-red-400">*</span>
              </label>
              <input
                id="userId-input"
                type="number"
                min="1"
                value={userId}
                onChange={e => setUserId(e.target.value)}
                className="form-input"
                placeholder="e.g. 1"
                required
              />
              <p className="text-xs text-slate-500 mt-1">
                Database user ID associated with the profile.
              </p>
            </div>

            <div>
              <label htmlFor="token-input" className="form-label">
                JWT Bearer Token <span className="text-xs text-slate-400 font-normal">(optional if public)</span>
              </label>
              <input
                id="token-input"
                type="password"
                value={token}
                onChange={e => setToken(e.target.value)}
                className="form-input font-mono text-xs"
                placeholder="Paste Bearer token from /api/v1/auth/login"
                autoComplete="off"
              />
              <p className="text-xs text-slate-500 mt-1">
                Required for authenticated backend environments.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3 mt-4 pt-3 border-t border-white/5">
            <button
              id="fetch-profile-btn"
              type="button"
              onClick={handleFetchProfile}
              disabled={fetchState === 'loading' || !userId.trim()}
              className="btn-secondary text-xs py-2 px-4"
            >
              {fetchState === 'loading' ? 'Loading Profile…' : 'Fetch Existing Profile'}
            </button>

            {isExistingProfile && (
              <button
                type="button"
                onClick={handleResetForm}
                className="text-xs text-slate-400 hover:text-slate-200 underline py-2 px-2 transition-colors"
              >
                Switch to Create New Profile
              </button>
            )}

            <div className="ml-auto text-xs">
              {isExistingProfile ? (
                <span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                  Mode: Update Existing Profile
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-400 border border-white/10">
                  Mode: Create New Profile
                </span>
              )}
            </div>
          </div>

          {fetchError && (
            <div className="mt-3 px-3 py-2 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-300 text-xs">
              {fetchError}
            </div>
          )}
        </section>

        {/* Profile Form */}
        <form onSubmit={handleSubmit} noValidate>
          {/* Section 1: Farmer Identity */}
          <section className="glass-card p-5 sm:p-6 mb-6" aria-labelledby="identity-heading">
            <h2 id="identity-heading" className="text-base font-semibold text-slate-200 mb-4 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              2. Basic Information
            </h2>

            <div className="grid gap-4">
              <div>
                <label htmlFor="fullName" className="form-label">
                  Full Name <span className="text-red-400">*</span>
                </label>
                <input
                  id="fullName"
                  name="fullName"
                  type="text"
                  value={form.fullName}
                  onChange={handleInputChange}
                  className="form-input"
                  placeholder="e.g. Ramesh Patel"
                  required
                />
                {fieldErrors.fullName && (
                  <p className="text-xs text-red-400 mt-1">{fieldErrors.fullName}</p>
                )}
              </div>

              <div className="grid sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="phoneNumber" className="form-label">
                    Phone Number <span className="text-xs text-slate-500 font-normal">(optional)</span>
                  </label>
                  <input
                    id="phoneNumber"
                    name="phoneNumber"
                    type="tel"
                    value={form.phoneNumber}
                    onChange={handleInputChange}
                    className="form-input"
                    placeholder="+919876543210"
                  />
                  {fieldErrors.phoneNumber && (
                    <p className="text-xs text-red-400 mt-1">{fieldErrors.phoneNumber}</p>
                  )}
                </div>

                <div>
                  <label htmlFor="preferredLanguage" className="form-label">
                    Preferred Language <span className="text-red-400">*</span>
                  </label>
                  <select
                    id="preferredLanguage"
                    name="preferredLanguage"
                    value={form.preferredLanguage}
                    onChange={handleInputChange}
                    className="form-input"
                  >
                    <option value="en">English (en)</option>
                    <option value="hi">Hindi (hi)</option>
                    <option value="mr">Marathi (mr)</option>
                    <option value="gu">Gujarati (gu)</option>
                    <option value="pa">Punjabi (pa)</option>
                    <option value="ta">Tamil (ta)</option>
                    <option value="te">Telugu (te)</option>
                    <option value="kn">Kannada (kn)</option>
                  </select>
                  {fieldErrors.preferredLanguage && (
                    <p className="text-xs text-red-400 mt-1">{fieldErrors.preferredLanguage}</p>
                  )}
                </div>
              </div>

              <div className="grid sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="primaryState" className="form-label">
                    State <span className="text-xs text-slate-500 font-normal">(optional)</span>
                  </label>
                  <input
                    id="primaryState"
                    name="primaryState"
                    type="text"
                    value={form.primaryState}
                    onChange={handleInputChange}
                    className="form-input"
                    placeholder="e.g. Gujarat"
                  />
                  {fieldErrors.primaryState && (
                    <p className="text-xs text-red-400 mt-1">{fieldErrors.primaryState}</p>
                  )}
                </div>

                <div>
                  <label htmlFor="primaryDistrict" className="form-label">
                    District <span className="text-xs text-slate-500 font-normal">(optional)</span>
                  </label>
                  <input
                    id="primaryDistrict"
                    name="primaryDistrict"
                    type="text"
                    value={form.primaryDistrict}
                    onChange={handleInputChange}
                    className="form-input"
                    placeholder="e.g. Anand"
                  />
                  {fieldErrors.primaryDistrict && (
                    <p className="text-xs text-red-400 mt-1">{fieldErrors.primaryDistrict}</p>
                  )}
                </div>

                <div>
                  <label htmlFor="yearsOfExperience" className="form-label">
                    Experience (Years) <span className="text-red-400">*</span>
                  </label>
                  <input
                    id="yearsOfExperience"
                    name="yearsOfExperience"
                    type="number"
                    min="0"
                    max="99"
                    value={form.yearsOfExperience}
                    onChange={handleInputChange}
                    className="form-input"
                    required
                  />
                  {fieldErrors.yearsOfExperience && (
                    <p className="text-xs text-red-400 mt-1">{fieldErrors.yearsOfExperience}</p>
                  )}
                </div>
              </div>
            </div>
          </section>

          {/* Section 2: Land Details */}
          <section className="glass-card p-5 sm:p-6 mb-6" aria-labelledby="land-heading">
            <h2 id="land-heading" className="text-base font-semibold text-slate-200 mb-4 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              3. Land Details
            </h2>

            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="totalLandHectares" className="form-label">
                  Total Land Area (hectares) <span className="text-xs text-slate-500 font-normal">(optional)</span>
                </label>
                <input
                  id="totalLandHectares"
                  name="totalLandHectares"
                  type="number"
                  min="0.0001"
                  step="0.0001"
                  value={form.totalLandHectares}
                  onChange={handleInputChange}
                  className="form-input"
                  placeholder="e.g. 5.50"
                />
                <p className="text-xs text-slate-500 mt-1">
                  Total parcel size in hectares. Must be &gt; 0 if supplied.
                </p>
                {fieldErrors.totalLandHectares && (
                  <p className="text-xs text-red-400 mt-1">{fieldErrors.totalLandHectares}</p>
                )}
              </div>

              <div>
                <label htmlFor="cultivableHectares" className="form-label">
                  Cultivable Area (hectares) <span className="text-xs text-slate-500 font-normal">(optional)</span>
                </label>
                <input
                  id="cultivableHectares"
                  name="cultivableHectares"
                  type="number"
                  min="0.0001"
                  step="0.0001"
                  value={form.cultivableHectares}
                  onChange={handleInputChange}
                  className="form-input"
                  placeholder="e.g. 4.75"
                />
                <p className="text-xs text-slate-500 mt-1">
                  Portion of land actively cultivable. Must not exceed total land.
                </p>
                {fieldErrors.cultivableHectares && (
                  <p className="text-xs text-red-400 mt-1">{fieldErrors.cultivableHectares}</p>
                )}
              </div>
            </div>
          </section>

          {/* Section 3: Soil & Irrigation */}
          <section className="glass-card p-5 sm:p-6 mb-6" aria-labelledby="soil-irrigation-heading">
            <h2 id="soil-irrigation-heading" className="text-base font-semibold text-slate-200 mb-4 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              4. Soil & Irrigation
            </h2>

            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="soilType" className="form-label">
                  Soil Classification <span className="text-xs text-slate-500 font-normal">(optional)</span>
                </label>
                <select
                  id="soilType"
                  name="soilType"
                  value={form.soilType}
                  onChange={handleInputChange}
                  className="form-input"
                >
                  <option value="">— Select Soil Type —</option>
                  <option value="ALLUVIAL">Alluvial Soil</option>
                  <option value="BLACK">Black Soil (Regur)</option>
                  <option value="RED">Red Soil</option>
                  <option value="LATERITE">Laterite Soil</option>
                  <option value="SANDY">Sandy Soil</option>
                  <option value="LOAMY">Loamy Soil</option>
                  <option value="CLAYEY">Clayey Soil</option>
                  <option value="OTHER">Other</option>
                </select>
                {fieldErrors.soilType && (
                  <p className="text-xs text-red-400 mt-1">{fieldErrors.soilType}</p>
                )}
              </div>

              <div>
                <label htmlFor="irrigationSource" className="form-label">
                  Primary Irrigation Source <span className="text-xs text-slate-500 font-normal">(optional)</span>
                </label>
                <select
                  id="irrigationSource"
                  name="irrigationSource"
                  value={form.irrigationSource}
                  onChange={handleInputChange}
                  className="form-input"
                >
                  <option value="">— Select Irrigation Source —</option>
                  <option value="BOREWELL">Borewell</option>
                  <option value="OPEN_WELL">Open Well</option>
                  <option value="CANAL">Canal Irrigation</option>
                  <option value="RIVER">River Lift</option>
                  <option value="RAIN_FED">Rain-Fed (Dryland)</option>
                  <option value="TANK">Farm Pond / Tank</option>
                  <option value="DRIP">Drip Irrigation</option>
                  <option value="SPRINKLER">Sprinkler System</option>
                  <option value="OTHER">Other</option>
                </select>
                {fieldErrors.irrigationSource && (
                  <p className="text-xs text-red-400 mt-1">{fieldErrors.irrigationSource}</p>
                )}
              </div>
            </div>
          </section>

          {/* Section 4: Water Availability */}
          <section className="glass-card p-5 sm:p-6 mb-6" aria-labelledby="water-heading">
            <h2 id="water-heading" className="text-base font-semibold text-slate-200 mb-4 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              5. Water Availability
            </h2>

            <div>
              <label htmlFor="waterAvailabilityMonths" className="form-label">
                Reliable Water Access (Months per Year) <span className="text-xs text-slate-500 font-normal">(optional)</span>
              </label>
              <div className="grid sm:grid-cols-2 gap-4 items-center">
                <input
                  id="waterAvailabilityMonths"
                  name="waterAvailabilityMonths"
                  type="number"
                  min="0"
                  max="12"
                  value={form.waterAvailabilityMonths}
                  onChange={handleInputChange}
                  className="form-input"
                  placeholder="0 to 12"
                />
                <p className="text-xs text-slate-400">
                  Number of months during the agricultural year with dependable water supply for crop cycles.
                </p>
              </div>
              {fieldErrors.waterAvailabilityMonths && (
                <p className="text-xs text-red-400 mt-1">{fieldErrors.waterAvailabilityMonths}</p>
              )}
            </div>
          </section>

          {/* Section 5: Financial Information & Risk Appetite */}
          <section className="glass-card p-5 sm:p-6 mb-6" aria-labelledby="financial-heading">
            <h2 id="financial-heading" className="text-base font-semibold text-slate-200 mb-4 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              6. Financial & Risk Parameters
            </h2>

            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="workingCapitalInr" className="form-label">
                  Available Working Capital (₹ INR) <span className="text-xs text-slate-500 font-normal">(optional)</span>
                </label>
                <input
                  id="workingCapitalInr"
                  name="workingCapitalInr"
                  type="number"
                  min="0"
                  step="100"
                  value={form.workingCapitalInr}
                  onChange={handleInputChange}
                  className="form-input"
                  placeholder="e.g. 75000"
                />
                <p className="text-xs text-slate-500 mt-1">
                  Budget allocated for inputs, seeds, fertilizers, and operational expenses.
                </p>
                {fieldErrors.workingCapitalInr && (
                  <p className="text-xs text-red-400 mt-1">{fieldErrors.workingCapitalInr}</p>
                )}
              </div>

              <div>
                <label htmlFor="riskTolerance" className="form-label">
                  Risk Tolerance Profile <span className="text-xs text-slate-500 font-normal">(optional)</span>
                </label>
                <select
                  id="riskTolerance"
                  name="riskTolerance"
                  value={form.riskTolerance}
                  onChange={handleInputChange}
                  className="form-input"
                >
                  <option value="">— Select Risk Appetite —</option>
                  <option value="LOW">Low (Prioritize stable yields and downside protection)</option>
                  <option value="MEDIUM">Medium (Balanced risk and return)</option>
                  <option value="HIGH">High (Willing to adopt high-value or higher-variance crops)</option>
                </select>
                <p className="text-xs text-slate-500 mt-1">
                  Guides recommendations according to your financial loss threshold.
                </p>
                {fieldErrors.riskTolerance && (
                  <p className="text-xs text-red-400 mt-1">{fieldErrors.riskTolerance}</p>
                )}
              </div>
            </div>
          </section>

          {/* Global Alert / Feedback */}
          {globalError && (
            <div className="mb-6 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm flex items-start gap-2">
              <span className="text-base leading-none">⚠️</span>
              <div>{globalError}</div>
            </div>
          )}

          {successMessage && (
            <div className="mb-6 px-4 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm flex items-start gap-2">
              <span className="text-base leading-none">✓</span>
              <div>{successMessage}</div>
            </div>
          )}

          {/* Submit Actions */}
          <div className="flex flex-col sm:flex-row gap-3">
            <button
              id="submit-profile-btn"
              type="submit"
              disabled={isSubmitting}
              className="btn-primary w-full sm:w-auto flex-1 py-3 text-base"
            >
              {isSubmitting
                ? (isExistingProfile ? 'Saving Changes…' : 'Creating Profile…')
                : (isExistingProfile ? 'Update Farmer Profile' : 'Create Farmer Profile')}
            </button>
            <button
              type="button"
              onClick={handleResetForm}
              disabled={isSubmitting}
              className="btn-secondary py-3 px-6 text-sm"
            >
              Reset Form
            </button>
          </div>
        </form>

        {/* Existing Profile Overview Card */}
        {profileData && (
          <section className="glass-card p-6 mt-8 border-emerald-500/20" aria-labelledby="summary-heading">
            <div className="flex items-center justify-between mb-4 border-b border-white/10 pb-3">
              <h2 id="summary-heading" className="text-base font-semibold text-emerald-400 flex items-center gap-2">
                <span>🌾</span> Active Farm Profile Summary
              </h2>
              <span className="text-xs font-mono text-slate-400">
                Profile #{profileData.id} (Owner #{profileData.ownerId})
              </span>
            </div>

            <div className="grid sm:grid-cols-2 md:grid-cols-3 gap-4 text-xs">
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Farmer Name</span>
                <span className="text-slate-200 font-medium text-sm">{profileData.fullName}</span>
              </div>
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Total Land</span>
                <span className="text-slate-200 font-medium text-sm">
                  {profileData.totalLandHectares ? `${profileData.totalLandHectares} ha` : '—'}
                </span>
              </div>
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Cultivable Land</span>
                <span className="text-slate-200 font-medium text-sm">
                  {profileData.cultivableHectares ? `${profileData.cultivableHectares} ha` : '—'}
                </span>
              </div>
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Soil Classification</span>
                <span className="text-slate-200 font-medium">{profileData.soilType || '—'}</span>
              </div>
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Irrigation Source</span>
                <span className="text-slate-200 font-medium">{profileData.irrigationSource || '—'}</span>
              </div>
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Water Availability</span>
                <span className="text-slate-200 font-medium">
                  {profileData.waterAvailabilityMonths !== null && profileData.waterAvailabilityMonths !== undefined
                    ? `${profileData.waterAvailabilityMonths} months/year`
                    : '—'}
                </span>
              </div>
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Working Capital</span>
                <span className="text-slate-200 font-medium">
                  {profileData.workingCapitalInr !== null && profileData.workingCapitalInr !== undefined
                    ? `₹ ${profileData.workingCapitalInr.toLocaleString('en-IN')}`
                    : '—'}
                </span>
              </div>
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Risk Appetite</span>
                <span className="text-slate-200 font-medium">{profileData.riskTolerance || '—'}</span>
              </div>
              <div className="bg-white/5 p-3 rounded-lg border border-white/5">
                <span className="text-slate-500 block">Location</span>
                <span className="text-slate-200 font-medium">
                  {[profileData.primaryDistrict, profileData.primaryState].filter(Boolean).join(', ') || '—'}
                </span>
              </div>
            </div>
          </section>
        )}

        <footer className="mt-12 text-xs text-slate-600 text-center">
          AI Farm Strategy Engine · Phase 2A Farm Data Foundation
        </footer>
      </div>
    </div>
  );
}
